/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.api.ws.oba_rest_api.methods;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.InjectParam;
import com.sun.jersey.api.spring.Autowire;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.opentripplanner.api.ws.oba_rest_api.OneBusAwayApiCacheService;
import org.opentripplanner.api.ws.oba_rest_api.OneBusAwayRequestLogger;
import org.opentripplanner.api.ws.oba_rest_api.beans.*;
import org.opentripplanner.common.geometry.GeometryUtils;
import org.opentripplanner.common.model.T2;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.bike_rental.BikeRentalStationService;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.ServiceDay;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.edgetype.PatternHop;
import org.opentripplanner.routing.edgetype.PreAlightEdge;
import org.opentripplanner.routing.edgetype.PreBoardEdge;
import org.opentripplanner.routing.edgetype.TableTripPattern;
import org.opentripplanner.routing.edgetype.Timetable;
import org.opentripplanner.routing.edgetype.TimetableResolver;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.error.TransitTimesException;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.patch.Patch;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PatchService;
import org.opentripplanner.routing.services.TransitIndexService;
import org.opentripplanner.routing.transit_index.RouteVariant;
import org.opentripplanner.routing.transit_index.adapters.TripsModelInfo;
import org.opentripplanner.routing.trippattern.CanceledTripTimes;
import org.opentripplanner.routing.trippattern.ScheduledTripTimes;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.updater.stoptime.TimetableSnapshotSource;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;
import org.opentripplanner.updater.vehicle_location.VehicleLocationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.text.ParseException;
import java.util.*;

/**
 * A base class for imitating the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/index.html">OneBusAway REST API</a>.
 * <p>
 * The following methods are implements:
 * - agencies-with-coverage           {@link AgenciesWithCoverageMethod}
 * - agency                           {@link AgencyMethod}
 * - arrival-and-departure-for-stop   {@link ArrivalAndDepartureForStopMethod}
 * - arrivals-and-departures-for-stop {@link ArrivalsAndDeparturesForStopMethod}
 * - cancel-alarm                     -
 * - current-time                     {@link MetadataMethod}
 * - plan-trip                        {@link PlanTripMethod}, <i>not OBA compatible</i>
 * - register-alarm-for-arrival-and-departure-at-stop -
 * - report-problem-with-stop          -
 * - report-problem-with-trip          -
 * - route-ids-for-agency              {@link RouteIdsForAgencyMethod}
 * - route                             {@link RouteMethod}
 * - routes-for-agency                 {@link RoutesForAgencyMethod}
 * - routes-for-location               {@link RoutesForLocationMethod}
 * - schedule-for-stop
 * - shape                             {@link ShapeMethod}
 * - stop-ids-for-agency               {@link StopIdsForAgencyMethod}
 * - stop                              {@link StopMethod}
 * - stops-for-location                {@link StopsForLocationMethod}
 * - stops-for-route                   {@link StopsForRouteMethod}
 * - trip-details                      {@link TripDetailsMethod}
 * - trip-for-vehicle                  {@link TripForVehicleMethod}
 * - trip                              {@link TripMethod}
 * - trips-for-location                {@link TripsForLocationMethod}
 * - trips-for-route                   {@link TripsForRouteMethod}
 * - vehicles-for-agency               {@link VehiclesForAgencyMethod}
 * <p>
 * With the following "extras":
 * - metadata                          {@link MetadataMethod}
 * - alert                             {@link AlertMethod}
 * - search                            {@link SearchMethod}
 * - alert-search                      {@link AlertSearchMethod}
 * - trip-details                      {@link TripDetailsOTPMethod}
 * - route-details                     {@link RouteDetailsMethod}
 * - route-details-for-stop            {@link RouteDetailsForStopMethod}
 * - vehicles-for-location             {@link VehiclesForLocationMethod}
 * - vehicles-for-stop                 {@link VehiclesForStopMethod}
 * - vehicles-for-route                {@link VehiclesForRouteMethod}
 * - vehicle-for-trip                  {@link VehicleForTripMethod}
 * <p>
 * Differences
 * - StopV2Bean#direction
 * - RouteV2Bean#type
 */

@Slf4j
@Autowire
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
public abstract class OneBusAwayApiMethod<T> {
    private static final Logger LOG = LoggerFactory.getLogger(OneBusAwayApiMethod.class);
    private OneBusAwayRequestLogger requestLogger = new OneBusAwayRequestLogger();

    /**
     * The base API path which all methods are relative to.
     */
    public final static String API_BASE_PATH = "/{dialect:(?:oba|otp|mobile)}/api/where/";

    /**
     * The accepted content type extensions.
     */
    public final static String API_CONTENT_TYPE = ""; //"{contentType:(?:|\\.xml|\\.json)}";

    public static final int NEARBY_STOP_RADIUS = 100;

    /**
     * API key used to authenticate the client application.
     */
    @QueryParam("key")
    protected String apiKey;

    /**
     * The application version for the API key.
     */
    @QueryParam("appVersion")
    protected String appVersion;

    /**
     * The API version of the request. Currently only version 2 is supported.
     */
    @QueryParam("version")
    @DefaultValue("2")
    protected TransitResponseBuilder.ApiVersionWrapper apiVersion;

    /**
     * API response dialect, provided int the URL. May be either <code>otp</code> or <code>oba</code>.
     *
     * @see org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder.Dialect
     */
    @PathParam("dialect")
    protected TransitResponseBuilder.DialectWrapper dialect;

    /**
     * <code>routerId</code> to return the response for. Uses the default graph if not specified.
     */
    @QueryParam("routerId")
    protected String routerId;

    /**
     * Content-type of the returned response. Defaults to JSON if not provided.
     */
    @PathParam("contentType")
    protected String contentType;

    /**
     * Included the <code>references</code> section in the returned response. Referenced entities may be
     * requested one-at-a-time using the appropriate API methods.
     *
     * @see org.opentripplanner.api.ws.oba_rest_api.methods.AgencyMethod
     * @see org.opentripplanner.api.ws.oba_rest_api.methods.AlertMethod
     * @see org.opentripplanner.api.ws.oba_rest_api.methods.StopMethod
     * @see org.opentripplanner.api.ws.oba_rest_api.methods.RouteMethod
     * @see org.opentripplanner.api.ws.oba_rest_api.methods.TripMethod
     */
    @QueryParam("includeReferences")
    protected
    @DefaultValue("true")
    TransitResponseBuilder.ReferencesWrapper references;

    @Setter
    @InjectParam
    private GraphService graphService;

    @CookieParam("_ga")
    private String clientId;
    /*@Setter @InjectParam("GoogleAnalyticsId")*/ private String googleAnalyticsId;

    /**
     * Whether this is a BKK internal request. Defaults to false.
     */
    @Getter
    @HeaderParam("X-BKK-Internal-Request")
    @DefaultValue("false")
    private boolean internalRequest;

    @Context
    private UriInfo uriInfo;
    @Context
    private HttpContext httpContext;

    protected Graph graph;
    protected TransitIndexService transitIndexService;
    protected OneBusAwayApiCacheService cacheService;

    protected TransitResponseBuilder responseBuilder;

    @Getter(lazy = true)
    private final TimetableResolver timetableResolver = _getTimetableResolver();

    @GET
    public javax.ws.rs.core.Response processResponse() {
        OneBusAwayRequestLogger.LogRequest logRequest = requestLogger.startRequest(this, httpContext, uriInfo.getRequestUri(), clientId, apiKey, appVersion, internalRequest, dialect);

        graph = getGraph(routerId);
        if (graph == null) {
            return TransitResponseBuilder.getWsResponse(TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_NO_GRAPH, apiVersion.getApiVersion()));
        }

        transitIndexService = graph.getService(TransitIndexService.class);
        if (transitIndexService == null) {
            return TransitResponseBuilder.getWsResponse(TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_TRANSIT_INDEX_SERVICE, apiVersion.getApiVersion()));
        }

        createDefaultServices();

        responseBuilder = new TransitResponseBuilder(graph, apiVersion.getApiVersion(), references.getReferences(), dialect.getDialect(), internalRequest, httpContext.getRequest());

        TransitResponse<T> transitResponse;
        try {
            transitResponse = getResponse();
            logRequest.finishRequest(transitResponse);
        } catch (TransitTimesException e) {
            transitResponse = TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, apiVersion.getApiVersion());
            logRequest.exception(transitResponse, e);
        } catch (Exception e) {
            transitResponse = TransitResponseBuilder.getFailResponse(TransitResponse.Status.UNKNOWN_ERROR, "An error occured: " + e.getClass().getName(), apiVersion.getApiVersion());
            logRequest.exception(transitResponse, e);
            LOG.warn("Unhandled exception: ", e);
        }

        if (transitResponse.getStatus() == TransitResponse.Status.NOT_MODIFIED) {
            Response.ResponseBuilder response = Response.notModified();
            Response builtResponse = response.entity(transitResponse).build();
            throw new WebApplicationException(builtResponse);
        }
        /*else if(transitResponse.getStatus() == TransitResponse.Status.NOT_FOUND) {
            response = Response.status(Response.Status.NOT_FOUND);
        }
        else if(transitResponse.getStatus() != TransitResponse.Status.OK) {
            response = Response.serverError();
        }*/

        return TransitResponseBuilder.getWsResponse(transitResponse);
    }

    private void createDefaultServices() {
        cacheService = graph.getService(OneBusAwayApiCacheService.class);
        if (cacheService == null) {
            cacheService = new OneBusAwayApiCacheService();
            graph.putService(OneBusAwayApiCacheService.class, cacheService);
        }

        BikeRentalStationService service = graph.getService(BikeRentalStationService.class);
        if (service == null) {
            service = new BikeRentalStationService();
            graph.putService(BikeRentalStationService.class, service);
        }

        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if (vehicleLocationService == null) {
            VehicleLocationServiceImpl impl = new VehicleLocationServiceImpl();
            impl.setGraph(graph);
            vehicleLocationService = impl;
            graph.putService(VehicleLocationService.class, vehicleLocationService);
        }
    }

    abstract protected TransitResponse<T> getResponse();

    // ---- [] ---- //

    protected TransitTripDetails getTripDetails(AgencyAndId tripId, ServiceDate serviceDate,
                                                boolean includeStatus, boolean includeSchedule, boolean includeTrip) {

        if (transitIndexService.getTripPatternForTrip(tripId, serviceDate) == null) {
            return null;
        }

        Trip trip = getTrip(tripId, serviceDate);

        CalendarService calendarService = graph.getCalendarService();
        ServiceDay serviceDay = new ServiceDay(graph, serviceDate, calendarService, trip.getId().getAgencyId());

        long startTime = serviceDate.getAsDate(graph.getTimeZone()).getTime() / 1000;
        long endTime = serviceDate.next().getAsDate(graph.getTimeZone()).getTime() / 1000 - 1;

        if (!graph.transitFeedCovers(startTime) && graph.transitFeedCovers(endTime)) {
            return null;
        }

        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        if (!serviceDay.serviceIdRunning(pattern.getServiceId()))
            return null;

        TripTimes tripTimes = getTripTimesForTrip(tripId, serviceDate);
        if (tripTimes instanceof CanceledTripTimes)
            return null;

        return getTripDetails(trip, serviceDate, pattern, tripTimes, includeStatus, includeSchedule, includeTrip);
    }

    protected TransitTripDetails getTripDetails(Trip trip, ServiceDate serviceDate, TableTripPattern pattern,
                                                TripTimes tripTimes, boolean includeStatus, boolean includeSchedule, boolean includeTrip) {

        List<TransitStopTime> stopTimes = getStopTimesForTrip(serviceDate, pattern, tripTimes);

        VehicleLocation vehicle = null;
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if (vehicleLocationService != null) {
            vehicle = vehicleLocationService.getForTrip(trip.getId());
        }

        if (vehicle == null) {
            vehicle = createFakeVehicleForTrip(trip, serviceDate, pattern, tripTimes, stopTimes);
        }

        TransitTrip transitTrip = responseBuilder.getTrip(trip);
        transitTrip.setWheelchairAccessible(tripTimes.isWheelchairAccessible());
        responseBuilder.addToReferences(transitTrip);

        TransitStopTime first = stopTimes.get(0),
                last = stopTimes.get(stopTimes.size() - 1);
        long startTime = Math.min(first.hasDepartureTime() ? first.getDepartureTime() : Long.MAX_VALUE,
                first.hasPredictedDepartureTime() ? first.getPredictedDepartureTime() : Long.MAX_VALUE),
                endTime = Math.max(last.hasArrivalTime() ? last.getArrivalTime() : Long.MIN_VALUE,
                        last.hasPredictedArrivalTime() ? last.getPredictedArrivalTime() : Long.MIN_VALUE);
        RoutingRequest options = makeTraverseOptions(startTime, routerId);

        Collection<String> alertIds = getAlertsForTrip(trip.getId(), trip.getRoute().getId(), options, startTime, endTime);
        TransitTripStatus tripStatus = null;
        TransitTripStopTimes tripSchedule = null;

        if (includeTrip) {
            responseBuilder.addToReferences(trip);
        }
        if (includeStatus) {
            tripStatus = getTripStatus(trip, serviceDate, tripTimes, stopTimes, vehicle, alertIds);
        }
        if (includeSchedule) {
            tripSchedule = getTripSchedule(serviceDate, stopTimes); // mutates StopTimes to seconds-since-midnight
        }

        TransitTripDetails tripDetails = new TransitTripDetails();
        tripDetails.setTripId(trip.getId().toString());
        tripDetails.setServiceDate(serviceDate.getAsDate(graph.getTimeZone()).getTime());
        tripDetails.setFrequency(null);
        tripDetails.setSchedule(tripSchedule);
        tripDetails.setStatus(tripStatus);
        tripDetails.setSituationIds(alertIds);

        return tripDetails;
    }

    protected VehicleLocation createFakeVehicleForTrip(Trip trip, ServiceDate serviceDate, TableTripPattern pattern,
                                                       TripTimes tripTimes, List<TransitStopTime> stopTimes) {

        AgencyAndId tripId = trip.getId();
        AgencyAndId routeId = trip.getRoute().getId();

        long now = System.currentTimeMillis() / 1000;
        TransitStopTime nextStop = stopTimes.get(0);
        VehicleLocation.Status status = VehicleLocation.Status.STOPPED_AT;
        int hopIndex = -1;
        for (TransitStopTime stopTime : stopTimes) {
            if (stopTime.hasPredictedArrivalTime() && stopTime.getPredictedArrivalTime() >= now) {
                status = VehicleLocation.Status.IN_TRANSIT_TO;
                break;
            }
            if (stopTime.hasPredictedDepartureTime() && stopTime.getPredictedDepartureTime() >= now) {
                status = VehicleLocation.Status.STOPPED_AT;
                break;
            }
            if (stopTime.hasArrivalTime() && stopTime.getArrivalTime() >= now) {
                status = VehicleLocation.Status.IN_TRANSIT_TO;
                break;
            }
            if (stopTime.hasDepartureTime() && stopTime.getDepartureTime() >= now) {
                status = VehicleLocation.Status.STOPPED_AT;
                break;
            }
            nextStop = stopTime;
            hopIndex++;
        }

        double lat, lon;
        Float bearing = null;
        AgencyAndId stopId = parseAgencyAndId(nextStop.getStopId());

        if (status == VehicleLocation.Status.STOPPED_AT) {

            Stop stop = transitIndexService.getAllStops().get(stopId);
            lat = stop.getLat();
            lon = stop.getLon();
            if (stop.getDirection() != null) {
                bearing = Float.parseFloat(stop.getDirection());
            }
        } else {
            long arrivalTime = tripTimes.getArrivalTime(hopIndex);
            long departureTime = tripTimes.getDepartureTime(hopIndex);
            double factor = (now - departureTime) / (arrivalTime - departureTime);
            PatternHop patternHop = pattern.getPatternHops().get(hopIndex);
            LineString linestring = patternHop.getGeometry();
            LineString splitLineString = GeometryUtils.splitGeometryAtFraction(linestring, factor).getFirst();
            Point lastPoint = splitLineString.getEndPoint();
            lon = lastPoint.getX();
            lat = lastPoint.getY();
        }

        return new VehicleLocation(routeId, (float) lat, (float) lon, tripId, bearing, status, stopId, serviceDate);
    }

    protected TransitTripStopTimes getTripSchedule(ServiceDate serviceDate, Collection<TransitStopTime> stopTimes) {

        long midnight = serviceDate.getAsDate(graph.getTimeZone()).getTime() / 1000;
        for (TransitStopTime stopTime : stopTimes) {

            responseBuilder.addToReferences(transitIndexService.getAllStops().get(parseAgencyAndId(stopTime.getStopId())));

            Long arrivalTime = stopTime.hasArrivalTime() ? stopTime.getArrivalTime() : stopTime.getPredictedArrivalTime(),
                    departureTime = stopTime.hasDepartureTime() ? stopTime.getDepartureTime() : stopTime.getPredictedDepartureTime();

            if (arrivalTime == null) {
                arrivalTime = departureTime;
            }
            if (departureTime == null) {
                departureTime = arrivalTime;
            }

            stopTime.setArrivalTime(arrivalTime - midnight);
            stopTime.setDepartureTime(departureTime - midnight);

            if (stopTime.hasPredictedArrivalTime())
                stopTime.setPredictedArrivalTime(stopTime.getPredictedArrivalTime() - midnight);
            if (stopTime.hasPredictedDepartureTime())
                stopTime.setPredictedDepartureTime(stopTime.getPredictedDepartureTime() - midnight);
        }

        TransitTripStopTimes tripSchedule = new TransitTripStopTimes();

        tripSchedule.setFrequency(null);
        tripSchedule.setNextTripId(null);
        tripSchedule.setPreviousTripId(null);
        tripSchedule.setTimeZone(graph.getTimeZone().getDisplayName());
        tripSchedule.setStopTimes(stopTimes);

        return tripSchedule;
    }

    private TransitTripStatus getTripStatus(String stopId, TransitTrip transitTrip, TransitArrivalAndDeparture arrivalAndDeparture) {

        ServiceDate serviceDate = new ServiceDate(new Date(arrivalAndDeparture.getServiceDate()));
        AgencyAndId tripId = parseAgencyAndId(transitTrip.getId());
        Trip trip = getTrip(tripId, serviceDate);
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        TripTimes tripTimes = getTripTimesForTrip(tripId, serviceDate);
        List<TransitStopTime> stopTimes = getStopTimesForTrip(serviceDate, pattern, tripTimes);

        TransitStopTime first = stopTimes.get(0),
                last = stopTimes.get(stopTimes.size() - 1);
        long startTime = Math.min(first.hasDepartureTime() ? first.getDepartureTime() : Long.MAX_VALUE,
                first.hasPredictedDepartureTime() ? first.getPredictedDepartureTime() : Long.MAX_VALUE),
                endTime = Math.max(last.hasArrivalTime() ? last.getArrivalTime() : Long.MIN_VALUE,
                        last.hasPredictedArrivalTime() ? last.getPredictedArrivalTime() : Long.MIN_VALUE);
        RoutingRequest options = makeTraverseOptions(startTime, routerId);
        List<String> alertIds = getAlertsForTrip(tripId, trip.getRoute().getId(), options, startTime, endTime);

        VehicleLocation vehicleLocation = null;
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if (vehicleLocationService != null) {
            vehicleLocation = vehicleLocationService.getForTrip(tripId);
        }

        if (vehicleLocation == null) {
            vehicleLocation = createFakeVehicleForTrip(trip, serviceDate, pattern, tripTimes, stopTimes);
        }

        if (vehicleLocation != null) {
            Integer stopDiff = null;
            boolean reverse = false;
            String nextStopId = vehicleLocation.getStopId().toString();
            for (TransitStopTime stopTime : stopTimes) {
                if (stopTime.getStopId().equals(nextStopId)) {
                    if (stopDiff == null) {
                        stopDiff = 0;
                    } else {
                        break;
                    }
                }
                if (stopTime.getStopId().equals(stopId)) {
                    if (stopDiff == null) {
                        stopDiff = 0;
                        reverse = true;
                    } else {
                        break;
                    }
                }
                if (stopDiff != null) {
                    stopDiff += reverse ? -1 : +1;
                }
            }

            arrivalAndDeparture.setNumberOfStopsAway(stopDiff);
            arrivalAndDeparture.setDistanceFromStop(0.); // TODO
        }

        return getTripStatus(trip, serviceDate, tripTimes, stopTimes, vehicleLocation, alertIds);
    }

    protected TransitTripStatus getTripStatus(Trip trip, ServiceDate serviceDate, TripTimes timetable, List<TransitStopTime> stopTimes,
                                              VehicleLocation vehicleLocation, Collection<String> alertIds) {

        TransitTripStatus tripStatus = new TransitTripStatus();

        tripStatus.setActiveTripId(trip.getId().toString());
        tripStatus.setBlockTripSequence(0);
        tripStatus.setPredicted(!(timetable instanceof ScheduledTripTimes));
        tripStatus.setFrequency(null);
        tripStatus.setPhase("");
        tripStatus.setServiceDate(serviceDate.getAsDate(graph.getTimeZone()).getTime());
        tripStatus.setSituationIds(alertIds);
        tripStatus.setStatus("default");

        TransitStopTime nextStop = null;
        if (vehicleLocation != null) {
            String nextStopId = vehicleLocation.getStopId().toString();
            for (TransitStopTime stopTime : stopTimes) {
                if (stopTime.getStopId().equals(nextStopId)) {
                    nextStop = stopTime;
                    break;
                }
            }
        }

        if (nextStop != null && vehicleLocation != null) {
            long now = System.currentTimeMillis() / 1000,
                    nextArrivalTime = firstNotNullTime(nextStop.getPredictedArrivalTime(), nextStop.getPredictedDepartureTime(),
                            nextStop.getArrivalTime(), nextStop.getDepartureTime());

            if (vehicleLocation.getVehicleId() != null) {
                tripStatus.setVehicleId(vehicleLocation.getVehicleId().toString());
            }
            tripStatus.setClosestStop(vehicleLocation.getStopId().toString());
            tripStatus.setLastKnownLocation(new TransitCoordinatePoint(vehicleLocation.getLatitude(), vehicleLocation.getLongitude()));
            tripStatus.setPosition(new TransitCoordinatePoint(vehicleLocation.getLatitude(), vehicleLocation.getLongitude()));
            if (vehicleLocation.getBearing() != null) {
                tripStatus.setOrientation(90 + vehicleLocation.getBearing());
                tripStatus.setLastKnownOrientation(90 + vehicleLocation.getBearing());
            }
            tripStatus.setLastLocationUpdateTime(vehicleLocation.getTimestamp() * 1000);
            tripStatus.setLastUpdateTime(vehicleLocation.getTimestamp() * 1000);
            tripStatus.setLastKnownDistanceAlongTrip(.0); // TODO
            tripStatus.setTotalDistanceAlongTrip(.0); // TODO
            tripStatus.setDistanceAlongTrip(.0); // TODO
            tripStatus.setNextStop(nextStop.getStopId());
            tripStatus.setClosestStopTimeOffset((int) (nextArrivalTime - now));
            tripStatus.setNextStopTimeOffset((int) (nextArrivalTime - now));

            if (nextStop.hasPredictedArrivalTime() && nextStop.hasArrivalTime())
                tripStatus.setScheduleDeviation((int) (nextStop.getPredictedArrivalTime() - nextStop.getArrivalTime()));

        } else {
            int lastHop = timetable.getNumHops() - 1;
            tripStatus.setScheduleDeviation(timetable.getArrivalDelay(lastHop));
        }

        return tripStatus;
    }

    private Long firstNotNullTime(Long... times) {
        for (Long time : times) {
            if (time != null) {
                return time;
            }
        }
        return null;
    }

    protected List<TransitStopTime> getStopTimesForTrip(ServiceDate serviceDate, TableTripPattern pattern, TripTimes tripTimes) {

        List<TransitStopTime> stopTimes = new LinkedList<TransitStopTime>();

        long time = serviceDate.getAsDate(graph.getTimeZone()).getTime() / 1000;
        int numStops = pattern.getStops().size();
        for (int i = 0; i < numStops; ++i) {
            TransitStopTime stopTime = new TransitStopTime();
            AgencyAndId stopId = pattern.getStops().get(i).getId();
            Stop stop = transitIndexService.getAllStops().get(stopId);
            stopTime.setStopId(stopId.toString());

            TripTimes scheduledTripTimes = tripTimes.getScheduledTripTimes();

            if (i + 1 < numStops) {
                // Exclude stopheadsign from mobile API
                if (dialect.getDialect() != TransitResponseBuilder.Dialect.MOBILE) {
                    stopTime.setStopHeadsign(tripTimes.getHeadsign(i));
                }

                if (!tripTimes.isScheduled())
                    stopTime.setPredictedDepartureTime(time + tripTimes.getDepartureTime(i));
                if (scheduledTripTimes != null)
                    stopTime.setDepartureTime(time + scheduledTripTimes.getDepartureTime(i));
            }
            if (i > 0) {
                if (!tripTimes.isScheduled())
                    stopTime.setPredictedArrivalTime(time + tripTimes.getArrivalTime(i - 1));
                if (scheduledTripTimes != null)
                    stopTime.setArrivalTime(time + scheduledTripTimes.getArrivalTime(i - 1));
            }

            if (GtfsLibrary.isAgencyInternal(stop)) {
                continue;
            }

            // TODO: alerts?

            stopTimes.add(stopTime);
        }

        return stopTimes;
    }

    protected List<TransitArrivalAndDeparture> getArrivalsAndDeparturesForStop(long startTime, long endTime, AgencyAndId stopId) {
        List<T2<TransitScheduleStopTime, TransitTrip>> stopTimesWithTrips = getStopTimesForStop(startTime, endTime, stopId, false, true, -1);
        sortStopTimesWithTrips(stopTimesWithTrips);

        List<TransitArrivalAndDeparture> arrivalsAndDepartures = new LinkedList<TransitArrivalAndDeparture>();
        for (T2<TransitScheduleStopTime, TransitTrip> stopTimeWithTrip : stopTimesWithTrips) {
            TransitScheduleStopTime stopTime = stopTimeWithTrip.getFirst();
            TransitTrip trip = stopTimeWithTrip.getSecond();
            TransitArrivalAndDeparture arrivalAndDeparture = getArrivalAndDepartureFromStopTime(trip, stopTime);
            arrivalsAndDepartures.add(arrivalAndDeparture);
        }

        return arrivalsAndDepartures;
    }

    protected TransitArrivalAndDeparture getArrivalAndDepartureFromStopTime(TransitTrip trip, TransitScheduleStopTime stopTime) {
        TransitArrivalAndDeparture arrivalAndDeparture = new TransitArrivalAndDeparture();

        ServiceDate serviceDate = new ServiceDate();
        try {
            serviceDate = ServiceDate.parseString(stopTime.getServiceDate());
        } catch (ParseException ex) {
        }

        Route route = transitIndexService.getAllRoutes().get(parseAgencyAndId(trip.getRouteId()));

        arrivalAndDeparture.setArrivalEnabled(stopTime.hasArrivalTime());
        arrivalAndDeparture.setBlockStopSequence(0); // TODO
        arrivalAndDeparture.setDepartureEnabled(stopTime.hasDepartureTime());
        arrivalAndDeparture.setDistanceFromStop(0); // TODO
        arrivalAndDeparture.setFrequency(null);
        arrivalAndDeparture.setNumberOfStopsAway(0); // TODO
        arrivalAndDeparture.setPredicted(stopTime.hasPredictedArrivalTime() || stopTime.hasPredictedDepartureTime());
        arrivalAndDeparture.setPredictedArrivalTime(1000 * (stopTime.hasPredictedArrivalTime() ? stopTime.getPredictedArrivalTime() : 0));
        arrivalAndDeparture.setPredictedDepartureTime(1000 * (stopTime.hasPredictedDepartureTime() ? stopTime.getPredictedDepartureTime() : 0));
        arrivalAndDeparture.setRouteId(trip.getRouteId());
        arrivalAndDeparture.setRouteLongName(route.getLongName());
        arrivalAndDeparture.setRouteShortName(route.getShortName());
        arrivalAndDeparture.setScheduledArrivalTime(1000 * (stopTime.hasArrivalTime() ? stopTime.getArrivalTime() : stopTime.getDepartureTime()));
        arrivalAndDeparture.setScheduledDepartureTime(1000 * (stopTime.hasDepartureTime() ? stopTime.getDepartureTime() : stopTime.getArrivalTime()));
        arrivalAndDeparture.setServiceDate(serviceDate.getAsDate(graph.getTimeZone()).getTime());
        arrivalAndDeparture.setStopSequence(stopTime.getSequence());
        arrivalAndDeparture.setTripHeadsign(trip.getTripHeadsign());
        arrivalAndDeparture.setTripId(trip.getId());

        TransitTripStatus tripStatus = getTripStatus(stopTime.getStopId(), trip, arrivalAndDeparture);
        arrivalAndDeparture.setTripStatus(tripStatus);

        return arrivalAndDeparture;
    }

    protected List<T2<TransitScheduleStopTime, TransitTrip>> getStopTimesForStop(
            long startTime, long endTime, AgencyAndId stopId, boolean onlyDepartures, boolean keepStopIds, int limit) {

        PreAlightEdge preAlightEdge = transitIndexService.getPreAlightEdge(stopId);
        PreBoardEdge preBoardEdge = transitIndexService.getPreBoardEdge(stopId);

        if (preAlightEdge != null && preBoardEdge != null) {
            List<T2<TransitScheduleStopTime, TransitTrip>> alightingTimes = getStopTimesForPreAlightEdge(stopId.toString(), startTime, endTime, preAlightEdge, limit);
            List<T2<TransitScheduleStopTime, TransitTrip>> boardingTimes = getStopTimesForPreBoardEdge(stopId.toString(), startTime, endTime, preBoardEdge, limit);

            return mergeStopTimes(boardingTimes, alightingTimes, onlyDepartures, keepStopIds);
        } else {
            return Collections.emptyList();
        }
    }

    protected List<T2<TransitScheduleStopTime, TransitTrip>> mergeStopTimes(
            List<T2<TransitScheduleStopTime, TransitTrip>> boardingTimes,
            List<T2<TransitScheduleStopTime, TransitTrip>> alightingTimes,
            boolean onlyDepartures, boolean keepStopIds) {

        Map<T2<String, Integer>, T2<TransitScheduleStopTime, TransitTrip>> stopTimeMap = new HashMap<T2<String, Integer>, T2<TransitScheduleStopTime, TransitTrip>>();
        for (T2<TransitScheduleStopTime, TransitTrip> boardingTime : boardingTimes) {
            T2<String, Integer> key = new T2<String, Integer>(boardingTime.getFirst().getTripId(), boardingTime.getFirst().getSequence());
            if (!keepStopIds) {
                boardingTime.getFirst().setStopId(null);
            }
            stopTimeMap.put(key, boardingTime);
        }

        for (T2<TransitScheduleStopTime, TransitTrip> alightingTime : alightingTimes) {
            T2<String, Integer> key = new T2<String, Integer>(alightingTime.getFirst().getTripId(), alightingTime.getFirst().getSequence());
            if (!keepStopIds) {
                alightingTime.getFirst().setStopId(null);
            }
            if (stopTimeMap.containsKey(key)) {
                TransitScheduleStopTime boardingTime = stopTimeMap.get(key).getFirst();
                boardingTime.setArrivalTime(alightingTime.getFirst().getArrivalTime());
                boardingTime.setPredictedArrivalTime(alightingTime.getFirst().getPredictedArrivalTime());
            } else {
                if (!onlyDepartures) {
                    stopTimeMap.put(key, alightingTime);
                }
            }
        }

        List<T2<TransitScheduleStopTime, TransitTrip>> stopTimes = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>(stopTimeMap.values());
        sortStopTimesWithTrips(stopTimes);
        return stopTimes;
    }

    protected List<T2<TransitScheduleStopTime, TransitTrip>> getStopTimesForPreAlightEdge(String stopId, long startTime, long endTime,
                                                                                          PreAlightEdge edge, int limit) {

        RoutingRequest options = makeTraverseOptions(startTime, routerId);
        options.setArriveBy(true);

        List<T2<TransitScheduleStopTime, TransitTrip>> result = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>();
        for (Edge e : edge.getFromVertex().getIncoming()) {
            if (!(e instanceof TransitBoardAlight))
                continue;

            result.addAll(getStopTimesFromTransitBoardAlightEdge(stopId, startTime, endTime, options, (TransitBoardAlight) e, limit));
        }

        return result;
    }

    protected List<T2<TransitScheduleStopTime, TransitTrip>> getStopTimesForPreBoardEdge(String stopId, long startTime, long endTime,
                                                                                         PreBoardEdge edge, int limit) {

        RoutingRequest options = makeTraverseOptions(startTime, routerId);
        options.setArriveBy(false);

        List<T2<TransitScheduleStopTime, TransitTrip>> result = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>();
        for (Edge e : edge.getToVertex().getOutgoing()) {
            if (!(e instanceof TransitBoardAlight))
                continue;

            result.addAll(getStopTimesFromTransitBoardAlightEdge(stopId, startTime, endTime, options, (TransitBoardAlight) e, limit));
        }

        return result;
    }

    protected List<T2<TransitScheduleStopTime, TransitTrip>> getStopTimesFromTransitBoardAlightEdge(String stopId, long startTime, long endTime,
                                                                                                    RoutingRequest options, TransitBoardAlight tba, int limit) {

        List<T2<TransitScheduleStopTime, TransitTrip>> out = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>();

        State result;
        long time = options.isArriveBy() ? endTime : startTime;
        int stopIndex = tba.getStopIndex();

        do {
            // TODO verify options/state correctness
            State s0 = new State(options.isArriveBy() ? tba.getToVertex() : tba.getFromVertex(), time, options);
            result = tba.traverse(s0);
            if (result == null)
                break;
            time = result.getTimeSeconds();
            if (time > endTime || time < startTime || (limit > 0 && out.size() > limit))
                break;

            long midnight = result.getServiceDay().time(0);
            TripTimes tripTimes = result.getTripTimes();
            TripTimes scheduledTripTimes = tripTimes.getScheduledTripTimes();

            Trip trip = result.getBackTrip();
            if(!internalRequest && GtfsLibrary.isAgencyInternal(trip)) {
                time += options.isArriveBy() ? -1 : +1; // move to the next board time
                continue;
            }

            TransitTrip transitTrip = responseBuilder.getTrip(trip);
            transitTrip.setWheelchairAccessible(tripTimes.isWheelchairAccessible());

            TransitScheduleStopTime stopTime = new TransitScheduleStopTime();
            stopTime.setTripId(transitTrip.getId());
            stopTime.setStopId(stopId);
            stopTime.setSequence(stopIndex);
            stopTime.setServiceDate(responseBuilder.getServiceDateAsString(result.getServiceDay().getServiceDate()));

            Set<Alert> alerts = result.getBackAlerts();
            if(alerts != null && !alerts.isEmpty()) {
                List<String> alertIds = new LinkedList<String>();
                for(Alert alert : alerts) {
                    responseBuilder.addToReferences(alert);
                    alertIds.add(alert.alertId.toString());
                }
                stopTime.setAlertIds(alertIds);
            }

            if (tba.isBoarding()) {
                if(!tripTimes.isScheduled())
                    stopTime.setPredictedDepartureTime(midnight + tripTimes.getDepartureTime(stopIndex));
                if(scheduledTripTimes != null)
                    stopTime.setDepartureTime(midnight + scheduledTripTimes.getDepartureTime(stopIndex));
            } else {
                if(!tripTimes.isScheduled())
                    stopTime.setPredictedArrivalTime(midnight + tripTimes.getArrivalTime(stopIndex - 1));
                if(scheduledTripTimes != null)
                    stopTime.setArrivalTime(midnight + scheduledTripTimes.getArrivalTime(stopIndex - 1));
            }
            out.add(new T2<TransitScheduleStopTime, TransitTrip>(stopTime, transitTrip));

            responseBuilder.addToReferences(trip.getRoute());

            time += options.isArriveBy() ? -1 : +1; // move to the next board time
        } while (true);

        return out;
    }

    protected List<TransitVehicle> getTransitVehiclesForRoute(VehicleLocationService vehicleLocationService, AgencyAndId routeId) {
        List<VehicleLocation> vehicles = new ArrayList<VehicleLocation>(vehicleLocationService.getForRoute(routeId));

        List<TransitVehicle> transitVehicles = new ArrayList<TransitVehicle>(vehicles.size());
        for (VehicleLocation vehicle : vehicles) {
            TransitTrip transitTrip = null;
            TransitVehicle transitVehicle = responseBuilder.getVehicle(vehicle);

            if (vehicle.getTripId() != null) {
                Trip trip = getTrip(vehicle.getTripId(), vehicle.getServiceDate());
                if (!isInternalRequest() && GtfsLibrary.isAgencyInternal(trip)) {
                    continue;
                }
                transitVehicle.setDelay(getDelayForVehicle(vehicle));
                transitTrip = getTransitTrip(vehicle, vehicle.getTripId(), vehicle.getServiceDate());
            }

            if (transitTrip != null) {
                transitVehicle.setTripId(transitTrip.getId());
                responseBuilder.addToReferences(transitTrip);
            }

            transitVehicles.add(transitVehicle);
        }

        return transitVehicles;
    }

    protected TransitVehicle getTransitVehicleForTrip(VehicleLocationService vehicleLocationService, AgencyAndId tripId, ServiceDate serviceDate) {
        VehicleLocation vehicle = vehicleLocationService.getForTrip(tripId);
        if (vehicle == null) {
            return null;
        }

        if (serviceDate != null && !serviceDate.equals(vehicle.getServiceDate())) {
            return null;
        }

        TransitTrip transitTrip = getTransitTrip(vehicle, vehicle.getTripId(), vehicle.getServiceDate());
        TransitVehicle transitVehicle = responseBuilder.getVehicle(vehicle);
        if (transitTrip != null) {
            transitVehicle.setTripId(transitTrip.getId());
            responseBuilder.addToReferences(transitTrip);
        }

        transitVehicle.setDelay(getDelayForVehicle(vehicle));

        return transitVehicle;
    }

    protected TransitTrip getTransitTrip(VehicleLocation vehicleLocation, AgencyAndId tripId, ServiceDate serviceDate) {
        Trip trip = getTrip(tripId, serviceDate);
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);

        if (pattern != null && (internalRequest || !GtfsLibrary.isAgencyInternal(trip))) {
            TripTimes tripTimes = getTripTimesForTrip(tripId, serviceDate);

            if (tripTimes != null) {
                if (vehicleLocation.getStopId() != null) {
                    Stop vehicleStop = transitIndexService.getAllStops().get(vehicleLocation.getStopId());
                    responseBuilder.addToReferences(vehicleStop);
                }

                TransitTrip transitTrip = responseBuilder.getTrip(trip);
                transitTrip.setWheelchairAccessible(tripTimes.isWheelchairAccessible());

                return transitTrip;
            }
        }

        return null;
    }

    protected Integer getDelayForVehicle(VehicleLocation vehicle) {
        final AgencyAndId tripId = vehicle.getTripId();
        final ServiceDate serviceDate = vehicle.getServiceDate();
        final AgencyAndId stopId = vehicle.getStopId();

        if (!isInternalRequest() || tripId == null || stopId == null) {
            return null;
        }

        TableTripPattern tablePatternEdge = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        TripTimes tripTimes = getTripTimesForTrip(tripId, serviceDate);
        if (tablePatternEdge == null || tripTimes == null || tripTimes.isScheduled()) {
            return null;
        }

        int hopIndex = getHopIndexForVehicle(tablePatternEdge, stopId);
        return hopIndex < 0 ? tripTimes.getArrivalDelay(0) : tripTimes.getDepartureDelay(hopIndex);
    }

    private int getHopIndexForVehicle(TableTripPattern tablePatternEdge, AgencyAndId stopId) {
        List<Stop> stops = tablePatternEdge.getStops();
        for(int i = 0; i < stops.size(); ++i) {
            if(stops.get(i).getId().equals(stopId)) {
                return i - 1;
            }
        }
        return -1;
    }

    protected TripTimes getTripTimesForTrip(AgencyAndId tripId, ServiceDate serviceDate) {
        TableTripPattern tablePatternEdge = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        if (tablePatternEdge == null) {
            return null;
        }

        Timetable timetable = tablePatternEdge.getScheduledTimetable();
        if (getTimetableResolver() != null) {
            timetable = getTimetableResolver().resolve(tablePatternEdge, serviceDate);
        }
        if (timetable == null) {
            return null;
        }

        int tripIndex = timetable.getTripIndex(tripId);
        if (tripIndex < 0) {
            return null;
        }

        return timetable.getTripTimes(tripIndex);
    }

    protected List<String> getAlertsForStop(AgencyAndId stopId, RoutingRequest options, long startTime, long endTime) {

        Set<String> alertIds = new HashSet<String>();

        PatchService patchService = graph.getService(PatchService.class);
        if (patchService != null) {
            collectAndAddActivePatches(patchService.getStopPatches(stopId), options, startTime, endTime, alertIds);
        }

        return new ArrayList<String>(alertIds);
    }

    protected List<String> getAlertsForRoute(AgencyAndId routeId, RoutingRequest options, long startTime, long endTime) {
        Set<String> alertIds = new HashSet<String>();
        PatchService patchService = graph.getService(PatchService.class);
        if (patchService != null) {
            collectAndAddActivePatches(patchService.getRoutePatches(routeId), options, startTime, endTime, alertIds);
        }

        return new ArrayList<String>(alertIds);
    }

    protected List<String> getAlertsForTrip(AgencyAndId tripId, AgencyAndId routeId, RoutingRequest options, long startTime, long endTime) {
        return getAlertsForRoute(routeId, options, startTime, endTime); // TODO ~ return trip alerts
    }

    protected List<String> getAlertsForApp(RoutingRequest options, long startTime, long endTime) {
        Set<String> alertIds = new HashSet<String>();

        PatchService patchService = graph.getService(PatchService.class);
        if (patchService != null && apiKey != null) {
            collectAndAddActivePatches(patchService.getAppPatches(apiKey, appVersion), options, startTime, endTime, alertIds);
        }

        return new ArrayList<String>(alertIds);
    }

    private void collectAndAddActivePatches(Collection<Patch> patches, RoutingRequest options, long startTime, long endTime, Set<String> alertIds) {
        for (Patch patch : patches) {
            if (patch.activeDuring(options, startTime, endTime)) {
                Alert alert = patch.getAlert();
                if (alert != null) {
                    responseBuilder.addToReferences(alert);
                    alertIds.add(alert.alertId.toString());
                }
            }
        }
    }

    protected final static String CACHE_NEARBY_STOPS = "nearbyStops";

    protected final List<String> getNearbyStops(Stop stop) {
        List<TransitStop> nearbyTransitStops = cacheService.get(CACHE_NEARBY_STOPS, stop);
        if (nearbyTransitStops == null) {
            nearbyTransitStops = graph.streetIndex.getNearbyTransitStops(new Coordinate(stop.getLat(), stop.getLon()), NEARBY_STOP_RADIUS);
            cacheService.put(CACHE_NEARBY_STOPS, stop, nearbyTransitStops);
        }

        List<String> nearbyStopIds = new ArrayList<String>(nearbyTransitStops.size());
        for (TransitStop transitStop : nearbyTransitStops) {
            responseBuilder.addToReferences(transitStop.getStop());
            nearbyStopIds.add(transitStop.getStop().getId().toString());
        }

        return nearbyStopIds;
    }

    protected List<AgencyAndId> getRoutesForStop(AgencyAndId stopId) {
        return responseBuilder.getRoutesForStop(stopId);
    }

    // -- -- -- | -- -- -- //

    private TimetableResolver _getTimetableResolver() {
        TimetableSnapshotSource timetableSnapshotSource = graph.getTimetableSnapshotSource();
        if (timetableSnapshotSource == null) {
            return null;
        }

        return timetableSnapshotSource.getTimetableSnapshot();
    }

    private Graph getGraph(String routerId) {
        try {
            return graphService.getGraph(routerId);
        } catch (Exception e) {
            return null;
        }
    }

    protected AgencyAndId parseAgencyAndId(String fullId) {
        if (StringUtils.isEmpty(fullId) || !fullId.contains("_"))
            return null;
        String[] parts = fullId.split("_", 2);
        return new AgencyAndId(parts[0], parts[1]);
    }

    protected Trip getTrip(AgencyAndId tripId, ServiceDate serviceDate) {
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        return pattern != null ? pattern.getTrip(tripId) : null;
    }

    protected RoutingRequest makeTraverseOptions(long startTime, String routerId) {
        RoutingRequest options = new RoutingRequest();
        // if (graphService.getCalendarService() != null) {
        // options.setCalendarService(graphService.getCalendarService());
        // options.setServiceDays(startTime, agencies);
        // }
        // TODO: verify correctness
        options.dateTime = startTime;
        Collection<Vertex> vertices = graph.getVertices();
        Iterator<Vertex> it = vertices.iterator();
        options.setFromString(it.next().getLabel());
        options.setToString(it.next().getLabel());
        options.setRoutingContext(graph);
        options.internalRequest = isInternalRequest();
        return options;
    }

    protected final List<RouteVariant> getReferenceVariantsForRoute(AgencyAndId routeId) {
        String CACHE_REFERENCE_VARIANTS_FOR_ROUTE = "referenceVariantsForRoute-" + isInternalRequest();
        List<RouteVariant> filteredVariants = cacheService.<AgencyAndId, List<RouteVariant>>get(CACHE_REFERENCE_VARIANTS_FOR_ROUTE, routeId);
        if (filteredVariants != null) {
            return filteredVariants;
        }

        List<RouteVariant> routeVariants = transitIndexService.getVariantsForRoute(routeId);
        Iterable<RouteVariant> referenceVariants = Iterables.filter(routeVariants, new Predicate<RouteVariant>() {
            @Override
            public boolean apply(RouteVariant input) {
                boolean reference = false;
                for (TripsModelInfo tripsModelInfo : input.getTrips()) {
                    reference |= tripsModelInfo.isRouteReference();
                }
                return reference;
            }
        });

        filteredVariants = Lists.newArrayList(referenceVariants);

        /*if(isInternalRequest()) {
            Iterable<RouteVariant> shapeReferenceTrips = Iterables.filter(routeVariants, new Predicate<RouteVariant>() {
                @Override
                public boolean apply(RouteVariant input) {
                    boolean reference = false;
                    for(TripsModelInfo tripsModelInfo : input.getTrips()) {
                        reference |= tripsModelInfo.isShapeReference();
                    }
                    return reference;
                }
            });

            filteredVariants.addAll(Lists.newArrayList(shapeReferenceTrips));
        }*/

        cacheService.put(CACHE_REFERENCE_VARIANTS_FOR_ROUTE, routeId, filteredVariants);
        return filteredVariants;
    }

    protected void sortStopTimesWithTrips(List<T2<TransitScheduleStopTime, TransitTrip>> stopTimes) {
        Collections.sort(stopTimes, new Comparator<T2<TransitScheduleStopTime, TransitTrip>>() {

            @Override
            public int compare(T2<TransitScheduleStopTime, TransitTrip> t2a, T2<TransitScheduleStopTime, TransitTrip> t2b) {
                TransitScheduleStopTime a = t2a.getFirst();
                TransitScheduleStopTime b = t2b.getFirst();

                return SCHEDULE_COMPARATOR.compare(a, b);
            }
        });
    }

    protected void sortStopTimes(List<TransitScheduleStopTime> stopTimes) {
        Collections.sort(stopTimes, SCHEDULE_COMPARATOR);
    }

    protected static class TransitScheduleStopTimeComparator implements Comparator<TransitScheduleStopTime> {
        @Override
        public int compare(TransitScheduleStopTime a, TransitScheduleStopTime b) {
            long ret;

            Long a_arrivalTime = a.getArrivalTime();
            Long a_departureTime = a.getDepartureTime();

            Long b_arrivalTime = b.getArrivalTime();
            Long b_departureTime = b.getDepartureTime();

            if (a.getPredictedDepartureTime() != null)
                a_departureTime = a.getPredictedDepartureTime();

            if (a.getPredictedArrivalTime() != null)
                a_arrivalTime = a.getPredictedArrivalTime();

            if (b.getPredictedDepartureTime() != null)
                b_departureTime = b.getPredictedDepartureTime();

            if (b.getPredictedArrivalTime() != null)
                b_arrivalTime = b.getPredictedArrivalTime();

            if (a_departureTime != null && b_departureTime != null) {
                ret = a_departureTime - b_departureTime;
                if (ret != 0)
                    return (int) ret;
            }

            if (a_arrivalTime != null && b_arrivalTime != null) {
                ret = a_arrivalTime - b_arrivalTime;
                if (ret != 0)
                    return (int) ret;
            }

            if (a_departureTime != null && b_arrivalTime != null) {
                ret = a_departureTime - b_arrivalTime;
                if (ret != 0)
                    return (int) ret;
            }

            if (a_arrivalTime != null && b_departureTime != null) {
                ret = a_arrivalTime - b_departureTime;
                if (ret != 0)
                    return (int) ret;
            }

            return a.getTripId().compareTo(b.getTripId());
        }
    }

    protected static TransitScheduleStopTimeComparator SCHEDULE_COMPARATOR = new TransitScheduleStopTimeComparator();
}
