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

package org.opentripplanner.api.ws.oba_rest_api.beans;

import com.google.common.collect.Lists;
import com.sun.jersey.api.core.HttpRequestContext;
import com.vividsolutions.jts.geom.LineString;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.api.ws.Response;
import org.opentripplanner.api.ws.oba_rest_api.OneBusAwayApiCacheService;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.FrequencyBoard;
import org.opentripplanner.routing.edgetype.TableTripPattern;
import org.opentripplanner.routing.edgetype.TransitBoardAlight;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.patch.TranslatedString;
import org.opentripplanner.routing.services.TransitIndexService;
import org.opentripplanner.routing.transit_index.RouteVariant;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.util.PolylineEncoder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransitResponseBuilder {
    private final static int API_VERSION = 2;

    public static javax.ws.rs.core.Response getWsResponse(TransitResponse<?> entity) {
        return javax.ws.rs.core.Response.ok()
                .entity(entity)
                .header("X-BKK-Status", entity.getStatus())
                .build();
    }

    @AllArgsConstructor
	public static class DialectWrapper {

		@Getter
		private final Dialect dialect;

		public static DialectWrapper valueOf(String value) {
			return new DialectWrapper(Dialect.valueOf(value.toUpperCase()));
		}
	}

	@AllArgsConstructor
	public static class ReferencesWrapper {

		@Getter
		private final EnumSet<References> references;

		public static ReferencesWrapper valueOf(String value) {
			if("false".equals(value)) return new ReferencesWrapper(EnumSet.noneOf(References.class));
			if("compact".equals(value)) return new ReferencesWrapper(EnumSet.allOf(References.class));

            if(!StringUtils.isEmpty(value) && !value.equals("true")) {
                String[] values = value.split(",");
                EnumSet<References> ret = EnumSet.noneOf(References.class);
                for(String v : values) {
                    References rv = References.valueOf(v.toUpperCase());
                    if(rv != null) {
                        ret.add(rv);
                    }
                }
                return new ReferencesWrapper(ret);
            }

			return new ReferencesWrapper(EnumSet.of(References.AGENCIES, References.ROUTES, References.TRIPS, References.STOPS, References.ALERTS));
		}
	}

	public enum Dialect {
		OTP, OBA, MOBILE
    }

	public enum References {
		COMPACT, AGENCIES, ROUTES, TRIPS, STOPS, ALERTS
	}

    private Locale _locale;
    private Dialect _dialect;
    private TransitIndexService _transitIndexService;
    private OneBusAwayApiCacheService _cacheService;
    private OTPTransitReferences _references = new OTPTransitReferences();

    private EnumSet<References> _returnReferences;
	private boolean _internalRequest;

    public TransitResponseBuilder(Graph graph, EnumSet<References> references, Dialect dialect, boolean internalRequest, HttpRequestContext httpRequestContext) {
        _dialect = dialect;
        _returnReferences = references;
        _transitIndexService = graph.getService(TransitIndexService.class);
        _cacheService = graph.getService(OneBusAwayApiCacheService.class);
	    _internalRequest = internalRequest;
        _locale = httpRequestContext.getAcceptableLanguages().isEmpty() ? Locale.getDefault() : httpRequestContext.getAcceptableLanguages().get(0);
    }

    public OTPTransitReferences getReferences() {
        return _references;
    }

    /* RESPONSE */

    public TransitResponse<TransitEntryWithReferences<TransitMetadata>> getResponseForMetadata(TransitMetadata metadata) {
        return getOkResponse(entity(metadata));
    }

    public TransitResponse<TransitListEntryWithReferences<TransitAgencyWithCoverage>> getResponseForAgenciesWithCoverage(List<TransitAgencyWithCoverage> agenciesWithCoverage) {
        for(TransitAgencyWithCoverage agencyWithCoverage : agenciesWithCoverage) {
            addToReferences(_transitIndexService.getAgency(agencyWithCoverage.getAgencyId()));
        }
        return getOkResponse(list(false, agenciesWithCoverage));
    }

    public TransitResponse<TransitEntryWithReferences<TransitAgency>> getResponseForAgency(Agency agency) {
        return getOkResponse(entity(getAgency(agency)));
    }

	public TransitResponse<TransitEntryWithReferences<TransitAlert>> getResponseForAlert(Alert alert) {
		return getOkResponse(entity(getAlert(alert)));
	}

    public TransitResponse<TransitEntryWithReferences<TransitRoute>> getResponseForRoute(Route route) {
        return getOkResponse(entity(getRoute(route)));
    }

	public TransitResponse<TransitListEntryWithReferences<TransitRoute>> getResponseForRoutes(List<Route> routes) {
		return getOkResponse(list(false, getRoutes(routes)));
	}

    public TransitResponse<TransitListEntryWithReferences<TransitRoute>> getResponseForTransitRoutes(List<TransitRoute> transitRoutes) {
        return getOkResponse(list(false, transitRoutes));
    }

    public TransitResponse<TransitEntryWithReferences<TransitRouteDetails>> getResponseForRoute(Route route, List<RouteVariant> variants, List<RouteVariant> relatedVariants, List<String> alertIds) {
        return getOkResponse(entity(getRoute(route, variants, relatedVariants, alertIds)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitStopsForRoute>> getResponseForStopsForRoute(Route route, List<RouteVariant> variants, List<String> alertIds,
            boolean includePolylines) {

        return getOkResponse(entity(getStopsForRoute(route, variants, alertIds, includePolylines)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitTrip>> getResponseForTrip(Trip trip) {
        return getOkResponse(entity(getTrip(trip)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitTripDetails>> getResponseForTrip(TransitTripDetails tripDetails) {
        return getOkResponse(entity(tripDetails));
    }

    public TransitResponse<TransitEntryWithReferences<TransitTripDetailsOTP>> getResponseForTrip(TransitTrip trip, ServiceDate serviceDate, List<String> alertIds,
            List<Stop> stops, List<TransitStopTime> stopTimes, TransitVehicle vehicle, RouteVariant variant) {

        return getOkResponse(entity(getTrip(trip, serviceDate, alertIds, stops, stopTimes, vehicle, variant)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitStop>> getResponseForStop(Stop stop) {
        return getOkResponse(entity(getStop(stop)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitArrivalsAndDepartures>> getResponseForStop(Stop stop, boolean limitExceeded,
            List<TransitScheduleStopTime> stopTimes, List<String> alertIds, List<TransitTrip> trips, List<String> nearbyStopIds) {

        return getOkResponse(entity(limitExceeded, getArrivalsAndDepartures(stop, stopTimes, alertIds, trips, nearbyStopIds)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitStopWithArrivalsAndDepartures>> getResponseForStop(Stop stop,
            List<TransitArrivalAndDeparture> arrivalsAndDepartures, List<String> alertIds, List<String> nearbyStopIds) {

        return getOkResponse(entity(getArrivalsAndDeparturesOBA(stop, arrivalsAndDepartures, alertIds, nearbyStopIds)));
    }

	public TransitResponse<TransitListEntryWithReferences<TransitStop>> getResponseForStops(Collection<Stop> stops, Map<String, List<String>> alertIds) {
        List<TransitStop> transitStops = new ArrayList<TransitStop>(stops.size());
        for(Stop stop : stops) {
            TransitStop transitStop = getStop(stop, alertIds.get(stop.getId().toString()));

            // Stops having no departures should be ignored from stopsForLocation on mobile platform
            if(_dialect == Dialect.MOBILE && transitStop.getRouteIds().isEmpty()) {
                continue; 
            }
            
            transitStops.add(transitStop);
        }
        return getOkResponse(list(false, transitStops));
    }

    public TransitResponse<TransitEntryWithReferences<TransitSchedule>> getResponseForStopSchedule(Stop stop, TransitSchedule schedule) {
        addToReferences(stop);
        return getOkResponse(entity(schedule));
    }

    public TransitResponse<TransitEntryWithReferences<TransitVehicle>> getResponseForVehicle(VehicleLocation vehicle) {
        return getOkResponse(entity(getVehicle(vehicle)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitPolyline>> getResponseForLineString(LineString linestring) {
        return getOkResponse(entity(getPolyline(linestring)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitSearch>> getResponseForSearch(String query, List<String> alertIds) {
        return getOkResponse(entity(getSearch(query, alertIds)));
    }

    public TransitResponse<TransitEntryWithReferences<TransitSearch>> getResponseForSearch(String query, List<String> stopIds, List<String> routeIds, List<String> alertIds) {
        return getOkResponse(entity(getSearch(query, stopIds, routeIds, alertIds)));
    }

    public TransitResponse<TransitEntryWithReferences<Response>> getResponseForTripPlan(Response plan) {
        return getOkResponse(entity(plan));
    }

    public TransitResponse<TransitEntryWithReferences<Response>> getResponseForErrorTripPlan(TransitResponse.Status status, Response plan) {
        return getFailResponse(status, plan.getError().getMsg(), entity(plan));
    }

    public <B> TransitResponse<TransitListEntryWithReferences<B>> getResponseForList(List<B> list) {
        return getOkResponse(list(false, list));
    }

    public <B> TransitResponse<TransitListEntryWithReferences<B>> getResponseForList(boolean limitExceeded, List<B> list) {
        return getOkResponse(list(limitExceeded, list));
    }

    private <T> TransitResponse<T> getOkResponse(T data) {
        return new TransitResponse<T>(API_VERSION, TransitResponse.Status.OK, "OK", data);
    }

    public static <T> TransitResponse<T> getFailResponse() {
        return getFailResponse(TransitResponse.Status.UNKNOWN_ERROR, "An unknown error occured...");
    }

    public static <T> TransitResponse<T> getFailResponse(String text) {
        return new TransitResponse<T>(API_VERSION, TransitResponse.Status.UNKNOWN_ERROR, text, null);
    }

    public static <T> TransitResponse<T> getFailResponse(TransitResponse.Status status) {
        return new TransitResponse<T>(API_VERSION, status, null, null);
    }

    public static <T> TransitResponse<T> getFailResponse(TransitResponse.Status status, String text) {
        return new TransitResponse<T>(API_VERSION, status, text, null);
    }

    public static <T> TransitResponse<T> getFailResponse(TransitResponse.Status status, String text, T data) {
        return new TransitResponse<T>(API_VERSION, status, text, data);
    }
    
    /* SUB-RESPONSE */

    private final static String CACHE_TRANSIT_VARIANT = "transitVariant";
    public TransitRouteVariant getTransitVariant(RouteVariant variant) {
        List<String> stopIds = new ArrayList<String>(variant.getStops().size());
        for(Stop stop : variant.getStops()) {
            if(GtfsLibrary.isAgencyInternal(stop))
                continue;

            addToReferences(stop);
            stopIds.add(stop.getId().toString());
        }

        if(_cacheService.<RouteVariant, TransitRouteVariant>containsKey(CACHE_TRANSIT_VARIANT, variant)) {
            return _cacheService.<RouteVariant, TransitRouteVariant>get(CACHE_TRANSIT_VARIANT, variant);
        }
        
        TransitRouteVariant transitVariant = new TransitRouteVariant();
        transitVariant.setName(variant.getName());
        transitVariant.setHeadsign(variant.getHeadsign());
        transitVariant.setDirection(variant.getDirection());
        transitVariant.setPolyline(getPolyline(variant.getGeometry()));
        transitVariant.setStopIds(stopIds);
        
        _cacheService.<RouteVariant, TransitRouteVariant>put(CACHE_TRANSIT_VARIANT, variant, transitVariant);
        return transitVariant;
    }
    
    public TransitTripDetailsOTP getTrip(TransitTrip trip, ServiceDate serviceDate, List<String> alertIds,
            List<Stop> stops, List<TransitStopTime> stopTimes, TransitVehicle vehicle, RouteVariant variant) {
        
        addToReferences(trip);
        
        for(Stop stop : stops)
            addToReferences(stop);
        
        TransitTripDetailsOTP transitTripDetails = new TransitTripDetailsOTP();
        transitTripDetails.setVehicle(vehicle);
        transitTripDetails.setAlertIds(alertIds);
        transitTripDetails.setTripId(trip.getId());
        transitTripDetails.setStopTimes(stopTimes);
        transitTripDetails.setServiceDate(getServiceDateAsString(serviceDate));

        if(!stopTimes.isEmpty()) {
            transitTripDetails.setPolyline(getPolyline(variant.getGeometry()));
        }

        return transitTripDetails;
    }
    
    public TransitArrivalsAndDepartures getArrivalsAndDepartures(Stop stop,
            List<TransitScheduleStopTime> stopTimes, List<String> alertIds, List<TransitTrip> trips, List<String> nearbyStopIds) {
        
        TransitArrivalsAndDepartures tad = new TransitArrivalsAndDepartures();
        tad.setStopId(stop.getId().toString());
        tad.setStopTimes(stopTimes);
        tad.setAlertIds(alertIds);
        tad.setNearbyStopIds(nearbyStopIds);
        addToReferences(stop);
        if(trips != null) {
            for(TransitTrip trip : trips) {
                addToReferences(trip);
            }
        }
        return tad;
    }
    
    public TransitStopWithArrivalsAndDepartures getArrivalsAndDeparturesOBA(Stop stop,
			List<TransitArrivalAndDeparture> arrivalsAndDepartures, List<String> alertIds, List<String> nearbyStopIds) {
        
        TransitStopWithArrivalsAndDepartures tad = new TransitStopWithArrivalsAndDepartures();
        tad.setStopId(stop.getId().toString());
        tad.setArrivalsAndDepartures(arrivalsAndDepartures);
        tad.setSituationIds(alertIds);
        tad.setNearbyStopIds(nearbyStopIds);
        addToReferences(stop);
        
        return tad;
    }

    public TransitVehicle getVehicle(VehicleLocation vehicle) {
        TransitVehicle transitVehicle = new TransitVehicle();
        transitVehicle.setVehicleId(vehicle.getVehicleId().toString());
        transitVehicle.setStatus(vehicle.getStatus());
        transitVehicle.setBearing(vehicle.getBearing());
        transitVehicle.setLocation(new TransitCoordinatePoint(vehicle.getLatitude(), vehicle.getLongitude()));
        transitVehicle.setLastUpdateTime(vehicle.getTimestamp());
        transitVehicle.setLicensePlate(vehicle.getLicensePlate());
        transitVehicle.setLabel(vehicle.getLabel());
        transitVehicle.setDeviated(vehicle.isDeviated());
        transitVehicle.setServiceDate(getServiceDateAsString(vehicle.getServiceDate()));
        transitVehicle.setCongestionLevel(vehicle.getCongestionLevel());
	    transitVehicle.setStopDistancePercent(vehicle.getStopDistancePercent());

	    if(_internalRequest) {
		    transitVehicle.setBusPhoneNumber(vehicle.getBusPhoneNumber());
		    transitVehicle.setDriverName(vehicle.getDriverName());
		    transitVehicle.setBlockId(vehicle.getBlockId());
		}


	    if(vehicle.getVehicleRouteType() != null) {
		    try {
		    transitVehicle.setVehicleRouteType(GtfsLibrary.getTraverseMode(vehicle.getVehicleRouteType()));
		    } catch(Exception e) {
			    transitVehicle.setVehicleRouteType(TraverseMode.BUS);
		    }
	    }

        if(vehicle.getRouteId() != null) {
            Route route = _transitIndexService.getAllRoutes().get(vehicle.getRouteId());
            if(_internalRequest || !GtfsLibrary.isAgencyInternal(route)) {
                transitVehicle.setRouteId(route.getId().toString());
                addToReferences(route);
            }
        }

        if(vehicle.getTripId() != null) {
            Trip trip = getTrip(vehicle.getTripId(), vehicle.getServiceDate());
            if(_internalRequest || !GtfsLibrary.isAgencyInternal(trip)) {
                transitVehicle.setTripId(vehicle.getTripId().toString());
            } else {
                transitVehicle.setRouteId(null);
            }
        }

        if(vehicle.getStopId() != null) {
            Stop stop = _transitIndexService.getAllStops().get(vehicle.getStopId());
            if(stop != null) {
                transitVehicle.setStopId(stop.getId().toString());
                addToReferences(stop);
            }
        }

        return transitVehicle;
    }

    public TransitSearch getSearch(String query, List<String> alertIds) {
        TransitSearch transitSearch = new TransitSearch();
        transitSearch.setQuery(query);
        transitSearch.setAlertIds(alertIds);
        
        return transitSearch;
    }

    public TransitSearch getSearch(String query, List<String> stopIds, List<String> routeIds, List<String> alertIds) {
        TransitSearch transitSearch = new TransitSearch();
        transitSearch.setQuery(query);
        transitSearch.setStopIds(stopIds);
        transitSearch.setRouteIds(routeIds);
        transitSearch.setAlertIds(alertIds);
        
        return transitSearch;
    }
    
    public List<TransitRoute> getRoutes(Collection<Route> routes) {
        List<TransitRoute> transitRoutes = new ArrayList<TransitRoute>(routes.size());
        
        for(Route route : routes) {
            transitRoutes.add(getRoute(route));
            addToReferences(route.getAgency());
        }
        
        return transitRoutes;
    }
    
    /* BASE ENTITIES */

    private final static String CACHE_AGENCY = "agency";
    public TransitAgency getAgency(Agency agency) {
        if(_cacheService.<Agency, TransitAgency>containsKey(CACHE_AGENCY, agency)) {
            return _cacheService.<Agency, TransitAgency>get(CACHE_AGENCY, agency);
        }
                
        TransitAgency transitAgency = new TransitAgency();
        transitAgency.setId(agency.getId());
        transitAgency.setLang(agency.getLang());
        transitAgency.setName(agency.getName());
        transitAgency.setPhone(agency.getPhone());
        transitAgency.setTimezone(agency.getTimezone());
        transitAgency.setUrl(agency.getUrl());
        
        _cacheService.<Agency, TransitAgency>put(CACHE_AGENCY, agency, transitAgency);
        return transitAgency;
    }
    
    public TransitRoute getRoute(Route route) {
        String CACHE_ROUTE = "route_" + _dialect + "_" + _returnReferences;
        addToReferences(route.getAgency());
        
        if(_cacheService.<Route, TransitRoute>containsKey(CACHE_ROUTE, route)) {
            return _cacheService.<Route, TransitRoute>get(CACHE_ROUTE, route);
        }
        
        TransitRoute transitRoute = new TransitRoute();
        transitRoute.setAgencyId(route.getAgency().getId());
        transitRoute.setColor(route.getColor());
	    if(!_returnReferences.contains(References.COMPACT)) {
			transitRoute.setDescription(route.getDesc());
	    }
        transitRoute.setId(route.getId().toString());
        transitRoute.setLongName(route.getLongName());
        transitRoute.setShortName(route.getShortName());
        transitRoute.setTextColor(route.getTextColor());
        transitRoute.setBikesAllowed(route.getBikesAllowed() == 1);
        if(_dialect == Dialect.OBA) {
	        transitRoute.setType(route.getType());
        } else {
	        transitRoute.setType(GtfsLibrary.getTraverseMode(route));
        }
        if(_dialect == Dialect.OBA && transitRoute.getShortName() == null) {
            transitRoute.setShortName("");
        }
        transitRoute.setUrl(route.getUrl());
        
        _cacheService.<Route, TransitRoute>put(CACHE_ROUTE, route, transitRoute);
        return transitRoute;
    }
    
//    private final String CACHE_ROUTE_DETAILS = "routeDetails";
    public TransitRouteDetails getRoute(Route route, List<RouteVariant> variants, List<RouteVariant> relatedVariants, List<String> alertIds) {
        addToReferences(route);

//        if(_cacheService.<Route, TransitRoute>containsKey(CACHE_ROUTE_DETAILS, route)) {
//            return _cacheService.<Route, TransitRoute>get(CACHE_ROUTE_DETAILS, route);
//        }

        List<TransitRouteVariant> transitVariants = new ArrayList<TransitRouteVariant>(variants.size());
        for(RouteVariant variant : variants) {
            TransitRouteVariant transitVariant = getTransitVariant(variant);
            transitVariants.add(transitVariant);
        }

        if(relatedVariants != null) {
            for(RouteVariant relatedVariant : relatedVariants) {
                TransitRouteVariant relatedTransitVariant = getTransitVariant(relatedVariant);
                relatedTransitVariant.setRouteId(relatedVariant.getRoute().getId().toString());
                transitVariants.add(relatedTransitVariant);
                addToReferences(relatedVariant.getRoute());
            }
        }

        TransitRouteDetails transitRoute = new TransitRouteDetails();
        transitRoute.setAgencyId(route.getAgency().getId());
        transitRoute.setColor(route.getColor());
        transitRoute.setDescription(route.getDesc());
        transitRoute.setId(route.getId().toString());
        transitRoute.setLongName(route.getLongName());
        transitRoute.setShortName(route.getShortName());
        transitRoute.setTextColor(route.getTextColor());
        transitRoute.setType(GtfsLibrary.getTraverseMode(route));
        transitRoute.setUrl(route.getUrl());
        transitRoute.setVariants(transitVariants);
        transitRoute.setAlertIds(alertIds);
        transitRoute.setBikesAllowed(route.getBikesAllowed() == 1);

//        _cacheService.<Route, TransitRoute>put(CACHE_ROUTE_DETAILS, route, transitRoute);
        return transitRoute;
    }
    
    // CACHE
    public TransitStopsForRoute getStopsForRoute(Route route, List<RouteVariant> variants, List<String> alertIds, boolean includePolylines) {

        List<TransitPolyline> polylines = new LinkedList<TransitPolyline>();
        List<String> stopIds = new LinkedList<String>();
        
        List<TransitStopGroup> stopGroups = new LinkedList<TransitStopGroup>();
        for(RouteVariant variant : variants) {
            
            TransitStopGroupName stopGroupName = new TransitStopGroupName();
            stopGroupName.setType("destination");
            stopGroupName.setName(variant.getHeadsign());
            stopGroupName.setNames(Collections.singletonList(variant.getHeadsign()));
            
            List<String> stopGroupStopIds = new LinkedList<String>();
            for(Stop stop : variant.getStops()) {
                addToReferences(stop);
                stopGroupStopIds.add(stop.getId().toString());
            }
            stopIds.addAll(stopGroupStopIds);
            
            TransitPolyline variantPolyline = getPolyline(variant.getGeometry());
            polylines.add(variantPolyline);
            
            List<TransitPolyline> stopGroupPolylines = new LinkedList<TransitPolyline>();
            stopGroupPolylines.add(variantPolyline);
            
            TransitStopGroup stopGroup = new TransitStopGroup();
            stopGroup.setId("" + variant.getId());
            stopGroup.setName(stopGroupName);
            stopGroup.setStopIds(stopGroupStopIds);
            if(includePolylines)
                stopGroup.setPolylines(stopGroupPolylines);
            stopGroup.setSubGroups(Collections.emptyList());
            
            stopGroups.add(stopGroup);
        }
        
        TransitStopGrouping stopGrouping = new TransitStopGrouping();
        stopGrouping.setStopGroups(stopGroups);
        stopGrouping.setType("direction");
        stopGrouping.setOrdered(true);
        
        List<TransitStopGrouping> stopGroupings = new ArrayList<TransitStopGrouping>(variants.size());
        stopGroupings.add(stopGrouping);
        
        TransitStopsForRoute stopsForRoute = new TransitStopsForRoute();
        stopsForRoute.setRouteId(route.getId().toString());
        stopsForRoute.setStopIds(stopIds);
        if(includePolylines)
            stopsForRoute.setPolylines(polylines);
        stopsForRoute.setSituationIds(alertIds);
        stopsForRoute.setStopGroupings(stopGroupings);
        
        addToReferences(route);
        return stopsForRoute;
    }
    
    private final String CACHE_POLYLINE = "polyline";
    public TransitPolyline getPolyline(LineString linestring) {
        if(_cacheService.<LineString, TransitPolyline>containsKey(CACHE_POLYLINE, linestring)) {
            return _cacheService.<LineString, TransitPolyline>get(CACHE_POLYLINE, linestring);
        }
        
        TransitPolyline polyline = new TransitPolyline();
        polyline.setLevels("");
        polyline.setLength(linestring.getNumPoints());
        polyline.setPoints(PolylineEncoder.createEncodings(linestring).getPoints());
        
        _cacheService.<LineString, TransitPolyline>put(CACHE_POLYLINE, linestring, polyline);
        return polyline;
    }
    
    public TransitTrip getTrip(Trip trip) {
        
        addToReferences(trip.getRoute());
        
        TransitTrip transitTrip = new TransitTrip();
        transitTrip.setServiceId(trip.getServiceId().toString());
        transitTrip.setBlockId(trip.getBlockId());
        transitTrip.setShapeId(trip.getId().toString());
        transitTrip.setTripHeadsign(trip.getTripHeadsign());
        transitTrip.setDirectionId(trip.getDirectionId());
        transitTrip.setId(trip.getId().toString());
        transitTrip.setRouteId(trip.getRoute().getId().toString());
        transitTrip.setTripShortName(trip.getTripShortName());
        transitTrip.setBikesAllowed(trip.getBikesAllowed() != 0 ? trip.getBikesAllowed() == 1 : trip.getRoute().getBikesAllowed() == 1);
        transitTrip.setWheelchairAccessible(trip.getWheelchairAccessible() == TableTripPattern.FLAG_WHEELCHAIR_ACCESSIBLE);
        
        return transitTrip;
    }

	public TransitStop getStop(Stop stop) {
		return getStop(stop, Lists.<String>newArrayList());
	}

    protected TransitStop getStop(Stop stop, List<String> alertIds) {
        String CACHE_STOP = "stop_" + _dialect + "_" + _internalRequest + "_" + alertIds;
        
        List<Route> routes = new ArrayList<Route>();
        for(AgencyAndId routeId : getRoutesForStop(stop.getId())) {
            Route route = _transitIndexService.getAllRoutes().get(routeId);
            addToReferences(route);
            routes.add(route);
        }

        if(_cacheService.<Stop, TransitStop>containsKey(CACHE_STOP, stop)) {
            return _cacheService.<Stop, TransitStop>get(CACHE_STOP, stop);
        }
        
        Collections.sort(routes, ROUTE_COMPARATOR);
        
        List<String> routeIds = new LinkedList<String>();
        for(Route route : routes) {
            routeIds.add(route.getId().toString());
        }
        
        TransitStop transitStop = new TransitStop();
        transitStop.setId(stop.getId().toString());
        transitStop.setCode(stop.getCode());
        transitStop.setName(stop.getName());
        transitStop.setLat(stop.getLat());
        transitStop.setLon(stop.getLon());
        transitStop.setDirection(stop.getDirection() == null ? "" : stop.getDirection());
        transitStop.setLocationType(stop.getLocationType());
	    transitStop.setRouteIds(routeIds);

        if(_dialect != Dialect.OBA && !StringUtils.isEmpty(stop.getParentStation())) {
            AgencyAndId parentStationId = new AgencyAndId(stop.getId().getAgencyId(), stop.getParentStation());
            Stop parentStation = _transitIndexService.getAllStops().get(parentStationId);
            if(parentStation != null) {
                addToReferences(parentStation);
                transitStop.setParentStationId(parentStationId.toString());
            } else {
                transitStop.setParentStationId(null);
            }
        }

	    if(_internalRequest && _dialect == Dialect.OTP) {
		    transitStop.setDescription(stop.getDesc());
	    }
        if(_dialect != Dialect.OBA) {
            transitStop.setWheelchairBoarding(1 == stop.getWheelchairBoarding());
        }
        if(_dialect != Dialect.OBA) {
            transitStop.setType(_transitIndexService.getModeForStop(stop.getId()));
        }
	    if(_dialect == Dialect.MOBILE) {
		    transitStop.setAlertIds(Lists.newArrayList(alertIds));
	    }
	    if(_dialect == Dialect.MOBILE || _dialect == Dialect.OTP) {
		    transitStop.setStopColorType(getStopColorTypeForStop(transitStop, routes, false));
	    }

        if(_dialect == Dialect.OBA) {
            if(stop.getDirection() != null) {
                transitStop.setDirection(getAngleAsDirection(Double.parseDouble(stop.getDirection())));
            }
        }

        _cacheService.<Stop, TransitStop>put(CACHE_STOP, stop, transitStop);
        return transitStop;
    }

	private String getStopColorTypeForStop(TransitStop transitStop, Collection<Route> routes, boolean incoming) {
		boolean hasTram = false,
				hasBus = false,
				hasTrolleyBus = false,
				hasFerry = false,
				hasNightBus = false,
				hasM1 = false, hasM2 = false, hasM3 = false, hasM4 = false,
				hasH5 = false, hasH6 = false, hasH7 = false, hasH8 = false, hasH9 = false;

		if(transitStop.getLocationType() == 2)
			return "ENTRANCE";

		for(Route route : routes) {
			if(route.getId().getId().startsWith("VP")
				|| route.getId().getId().startsWith("TP")
				|| route.getId().getId().startsWith("HP"))
			{ // A villamospótló senkit se érdekel?
                hasBus = true;
			}
			else if(route.getId().getId().startsWith("9")) {
				hasNightBus = true;
			}
			else if(GtfsLibrary.getTraverseMode(route) == TraverseMode.TRAM) {
				hasTram = true;
			}
			else if(GtfsLibrary.getTraverseMode(route) == TraverseMode.TROLLEYBUS) {
				hasTrolleyBus = true;
			}
			else if(GtfsLibrary.getTraverseMode(route) == TraverseMode.FERRY) {
				hasFerry = true;
			}
			else if(GtfsLibrary.getTraverseMode(route) == TraverseMode.BUS) {
				if(route.getId().getId().startsWith("9")) {
					hasNightBus = true;
				} else {
					hasBus = true;
				}
			}
			else if("M1".equals(route.getShortName())) {
				hasM1 = true;
			}
			else if("M2".equals(route.getShortName())) {
				hasM2 = true;
			}
			else if("M3".equals(route.getShortName())) {
				hasM3 = true;
			}
			else if("M4".equals(route.getShortName())) {
				hasM4 = true;
			}
			else if("H5".equals(route.getShortName())) {
				hasH5 = true;
			}
			else if("H6".equals(route.getShortName())) {
				hasH6 = true;
			}
			else if("H7".equals(route.getShortName())) {
				hasH7 = true;
			}
			else if("H8".equals(route.getShortName())) {
				hasH8 = true;
			}
			else if("H9".equals(route.getShortName())) {
				hasH9 = true;
			}
		}

		String ret = null;

		if(hasM1) ret = append(ret, "M1");
		if(hasM2) ret = append(ret, "M2");
		if(hasM3) ret = append(ret, "M3");
		if(hasM4) ret = append(ret, "M4");
		if(hasH5) ret = append(ret, "H5");
		if(hasH6) ret = append(ret, "H6");
		if(hasH7) ret = append(ret, "H7");
		if(hasH8) ret = append(ret, "H8");
		if(hasH9) ret = append(ret, "H9");
		if(hasFerry) ret = append(ret, "FERRY");
		if(hasTram) ret = append(ret, "TRAM");
		if(hasTrolleyBus) ret = append(ret, "TROLLEYBUS");
		if(hasBus) ret = append(ret, "BUS");

		if(hasNightBus && ret == null) ret = "NIGHTBUS";

		if(ret == null) {
            Collection<Route> incomingRoutes = getAllRoutesForStop(transitStop);
            if(incomingRoutes.isEmpty() || incoming) {
                return "OTHER";
            } else {
                return getStopColorTypeForStop(transitStop, incomingRoutes, true);
            }
        }

		return ret;
	}

    private Collection<Route> getAllRoutesForStop(TransitStop transitStop) {
        List<Route> routes = new ArrayList<Route>();
        for(AgencyAndId routeId : _transitIndexService.getIncomingRoutesForStop(AgencyAndId.convertFromString(transitStop.getId()))) {
            Route route = _transitIndexService.getAllRoutes().get(routeId);
            addToReferences(route);
            routes.add(route);
        }
        return routes;
    }

    private String append(String a, String b) {
		return a == null ? b : a + "-" + b;
	}

    public TransitNaturalLanguageString getTranslatedString(TranslatedString translatedString) {
        TransitNaturalLanguageString transitTranslatedString = new TransitNaturalLanguageString();
        if(translatedString.getTranslation(_locale.toLanguageTag()) != null) {
            transitTranslatedString.setLang(_locale.toLanguageTag());
            transitTranslatedString.setValue(deHTMLize(translatedString.getTranslation(_locale.toLanguageTag())));
        } else if(translatedString.getTranslation(_locale.getCountry()) != null) {
            transitTranslatedString.setLang(_locale.getCountry());
            transitTranslatedString.setValue(deHTMLize(translatedString.getTranslation(_locale.getCountry())));
        } else {
            transitTranslatedString.setLang("");
            transitTranslatedString.setValue(deHTMLize(translatedString.getSomeTranslation()));
        }
        
        return transitTranslatedString;
    }

    private static String deHTMLize(String htmlString) {
        String ret = htmlString.replace("<br/>", "\n");
        ret = ret.replace("<br>", "\n");
        ret = ret.replace("</p>", "\n");
        ret = ret.replaceAll("\\<.*?>","");
        return ret;
    }
    
    public TransitAlert getAlert(Alert alert) {
		final String CACHE_ALERT = "alerts_" + _dialect;

        if(alert.stopIds != null) {
            for(AgencyAndId stopId : alert.stopIds) {
                Stop stop = _transitIndexService.getAllStops().get(stopId);
                if(stop != null) {
                    addToReferences(stop);
                }
            }
        }
        
        if(alert.routeIds != null) {
            for(AgencyAndId routeId : alert.routeIds) {
                Route route = _transitIndexService.getAllRoutes().get(routeId);
                if(route != null) {
                    addToReferences(route);
                }
            }
        }
        
        if(_cacheService.<Alert, TransitAlert>containsKey(CACHE_ALERT, alert)) {
            TransitAlert cached = _cacheService.<Alert, TransitAlert>get(CACHE_ALERT, alert);
	        if(cached.getTimestamp() >= alert.timestamp) {
		       return cached;
	        }
        }
        
        TransitAlert transitAlert = new TransitAlert();
        transitAlert.setId(alert.alertId.toString());
        transitAlert.setUrl(alert.alertUrl);
        transitAlert.setHeader(alert.alertHeaderText);
        transitAlert.setDescription(alert.alertDescriptionText);
	    transitAlert.setTimestamp(alert.timestamp);

        if(alert.disableApp) {
            transitAlert.setDisableApp(true);
        }

        if(alert.effectiveStartDate != null)
            transitAlert.setStart(alert.effectiveStartDate.getTime() / 1000);
        if(alert.effectiveEndDate != null)
            transitAlert.setEnd(alert.effectiveEndDate.getTime() / 1000);

		if(_dialect == Dialect.MOBILE) {
			transitAlert.setStartText(alert.bpInternalStartTime);
			transitAlert.setEndText(alert.bpInternalEndTime);
		}

	    List<String> stopIds = new LinkedList<String>();
        if(alert.stopIds != null) {
            for(AgencyAndId stopId : alert.stopIds) {
                Stop stop = _transitIndexService.getAllStops().get(stopId);
                if(stop != null) {
                    stopIds.add(stopId.toString());
                }
            }
        }
	    transitAlert.setStopIds(stopIds);

	    List<String> routeIds = new ArrayList<String>();
        if(alert.routeIds != null) {
            List<Route> routes = new ArrayList<Route>(alert.routeIds.size());
            for(AgencyAndId routeId : alert.routeIds) {
                Route route = _transitIndexService.getAllRoutes().get(routeId);
                if(route != null)
                    routes.add(route);
            }
            Collections.sort(routes, ROUTE_COMPARATOR);
            
            for(Route route : routes) {
                routeIds.add(route.getId().toString());
            }
        }
	    transitAlert.setRouteIds(routeIds);

        _cacheService.<Alert, TransitAlert>put(CACHE_ALERT, alert, transitAlert);
        return transitAlert;
    }
    
    public Collection<TransitTimeRange> getTimeRange(Long start, Long end) {
        TransitTimeRange transitTimeRange = new TransitTimeRange();
        transitTimeRange.setFrom(start);
        transitTimeRange.setTo(end);
        return Collections.singleton(transitTimeRange);
    }
    
    public Collection<TransitSituationAffects> getAffectsFromAlert(TransitAlert alert) {
        Collection<TransitSituationAffects> affects = new HashSet<TransitSituationAffects>();
        
        if(alert.getRouteIds() != null) {
            for(String routeId : alert.getRouteIds()) {
                TransitSituationAffects transitSituationAffects = new TransitSituationAffects();
                transitSituationAffects.setRouteId(routeId);
                affects.add(transitSituationAffects);
            }
        }
        
        if(alert.getStopIds() != null) {
            for(String stopId : alert.getStopIds()) {
                TransitSituationAffects transitSituationAffects = new TransitSituationAffects();
                transitSituationAffects.setStopId(stopId);
                affects.add(transitSituationAffects);
            }
        }
        
        return affects;
    }
    
    public TransitSituation getSituation(TransitAlert transitAlert) {
        String CACHE_SITUATION = "situation-" + _locale.toLanguageTag();
        TransitSituation transitSituation = _cacheService.<TransitAlert, TransitSituation>get(CACHE_SITUATION, transitAlert);
        if(transitSituation != null) {
            return transitSituation;
        }
        
        transitSituation = new TransitSituation();
        transitSituation.setActiveWindows(getTimeRange(transitAlert.getStart(), transitAlert.getEnd()));
        transitSituation.setAllAffects(getAffectsFromAlert(transitAlert));
        transitSituation.setConsequences(Collections.<TransitSituationConsequences>emptySet());
        transitSituation.setCreationTime(System.currentTimeMillis());
        transitSituation.setDescription(getTranslatedString(transitAlert.getDescription()));
        transitSituation.setSummary(getTranslatedString(transitAlert.getHeader()));
        transitSituation.setId(transitAlert.getId());
        transitSituation.setPublicationWindows(getTimeRange(transitAlert.getStart(), transitAlert.getEnd()));
        transitSituation.setReasons(null);
        transitSituation.setSeverity(null);
        transitSituation.setUrl(getTranslatedString(transitAlert.getUrl()));
        
        _cacheService.<TransitAlert, TransitSituation>put(CACHE_SITUATION, transitAlert, transitSituation);
        return transitSituation;
    }
    
    /* HELPERS */

    public <B> TransitEntryWithReferences<B> entity(B entry) {
        return entity(false, entry);
    }

    public <B> TransitEntryWithReferences<B> entity(boolean limitExceeded, B entry) {
        if(_returnReferences.isEmpty())
            return new TransitEntryWithReferences<B>(limitExceeded, entry, null);
        else
            return new TransitEntryWithReferences<B>(limitExceeded, entry, getDialectReferences());
    }
    
    public <B> TransitListEntryWithReferences<B> list(boolean limitExceeded, List<B> entry) {
        if(_returnReferences.isEmpty())
            return new TransitListEntryWithReferences<B>(limitExceeded, entry, null);
        else
            return new TransitListEntryWithReferences<B>(limitExceeded, entry, getDialectReferences());
    }
    
    private TransitReferences getDialectReferences() {
        if(_dialect == Dialect.OTP) {
            return _references;
        }

	    if(_dialect == Dialect.MOBILE) {
		    return new MobileTransitReferences(
				    _references.getAgencies().values(),
				    _references.getRoutes().values(),
				    _references.getStops().values(),
				    _references.getTrips().values(),
				    _references.getAlerts().values());
	    }
        
        Collection<TransitSituation> situations = new ArrayList<TransitSituation>(_references.getAlerts().size());
        for(TransitAlert transitAlert : _references.getAlerts().values()) {
            situations.add(getSituation(transitAlert));
        }
        
        return new OBATransitReferences(_references.getAgencies().values(),
                                        _references.getRoutes().values(),
                                        _references.getStops().values(),
                                        _references.getTrips().values(),
                                        situations);
    }
    
    /* REFERENCES */
    
    public void addToReferences(Agency agency) {
        if(!_returnReferences.contains(References.AGENCIES)) {
            return;
        }
        if(_references.getAgencies().containsKey(agency.getId())) {
            return;
        }
        
        TransitAgency transitAgency = getAgency(agency);
        _references.addAgency(transitAgency);
    }

    public void addToReferences(Route route) {
        if(!_returnReferences.contains(References.ROUTES)) {
            return;
        }
        if(_references.getRoutes().containsKey(route.getId().toString())) {
            return;
        }
        
        TransitRoute transitRoute = getRoute(route);
        _references.addRoute(transitRoute);
    }

    public void addToReferences(Trip trip) {
        if(!_returnReferences.contains(References.TRIPS)) {
            return;
        }
        if(_references.getTrips().containsKey(trip.getId().toString())) {
            return;
        }
        
        TransitTrip transitTrip = getTrip(trip);
        _references.addTrip(transitTrip);
    }

    public void addToReferences(TransitTrip transitTrip) {
        if(!_returnReferences.contains(References.TRIPS)) {
            return;
        }
        if(_references.getTrips().containsKey(transitTrip.getId())) {
            return;
        }
        
        _references.addTrip(transitTrip);
    }

    public void addToReferences(Stop stop) {
        if(!_returnReferences.contains(References.STOPS)) {
            return;
        }
        if(_references.getStops().containsKey(stop.getId().toString())) {
            return;
        }
        
        TransitStop transitStop = getStop(stop);
        _references.addStop(transitStop);
    }

    public void addToReferences(TransitStop transitStop) {
        if(!_returnReferences.contains(References.STOPS)) {
            return;
        }
        if(_references.getStops().containsKey(transitStop.getId())) {
            return;
        }
        
        _references.addStop(transitStop);
    }

    public void addToReferences(Alert alert) {
        if(!_returnReferences.contains(References.ALERTS)) {
            return;
        }
        if(_references.getAlerts().containsKey(alert.alertId.toString())) {
            return;
        }
        
        TransitAlert transitAlert = getAlert(alert);
        _references.addAlert(transitAlert);
    }

    public final static String CACHE_ROUTEIDS_FOR_STOP = "routesIdsForStop";
    public final List<AgencyAndId> getRoutesForStop(AgencyAndId stopId) {
        if(_cacheService.<AgencyAndId, List<AgencyAndId>>containsKey(CACHE_ROUTEIDS_FOR_STOP, stopId)) {
            return _cacheService.<AgencyAndId, List<AgencyAndId>>get(CACHE_ROUTEIDS_FOR_STOP, stopId);
        }
        
        List<AgencyAndId> routeIds = findRoutesForStop(stopId);
        _cacheService.<AgencyAndId, List<AgencyAndId>>put(CACHE_ROUTEIDS_FOR_STOP, stopId, routeIds);
        return routeIds;
    }

    private List<AgencyAndId> findRoutesForStop(AgencyAndId stopId) {
        Set<AgencyAndId> out = new HashSet<AgencyAndId>();
        Edge edge = _transitIndexService.getPreBoardEdge(stopId);
        if (edge == null)
            return Collections.emptyList();

        for (Edge e: edge.getToVertex().getOutgoing()) {
            Trip trip = null;
            if (e instanceof TransitBoardAlight && ((TransitBoardAlight) e).isBoarding()) {
                TransitBoardAlight board = (TransitBoardAlight) e;
                trip = board.getPattern().getExemplar();
            }
            else if (e instanceof FrequencyBoard) {
                FrequencyBoard board = (FrequencyBoard) e;
                trip = board.getPattern().getTrip();
            }

            if(trip != null && !isOperationalReferenceTrip(trip))
                out.add(trip.getRoute().getId());
        }
        return new ArrayList<AgencyAndId>(out);
    }

    protected Trip getTrip(AgencyAndId tripId, ServiceDate serviceDate) {
        TableTripPattern pattern = _transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        return pattern != null ? pattern.getTrip(tripId) : null;
    }

    private boolean isOperationalReferenceTrip(Trip trip) {
        return trip.getId().getId().startsWith("REF_");
    }
    
    public final static String CACHE_SERVICE_DATE = "serviceDate";
    public final String getServiceDateAsString(ServiceDate serviceDate) {
        String serviceDateAsString = _cacheService.<ServiceDate, String>get(CACHE_SERVICE_DATE, serviceDate);
        if(serviceDateAsString != null) {
            return serviceDateAsString;
        }
        
        serviceDateAsString = serviceDate.getAsString();
        _cacheService.<ServiceDate, String>put(CACHE_SERVICE_DATE, serviceDate, serviceDateAsString);
        return serviceDateAsString;
    }
    
    private String getAngleAsDirection(double theta) {
        double t = 360 / 8;

        int r = (int) Math.floor((theta + t / 2) / t);

        switch (r) {
            case 0:
                return "N";
            case 1:
                return "NE";
            case 2:
                return "E";
            case 3:
                return "SE";
            case 4:
                return "S";
            case -1:
                return "NW";
            case -2:
                return "W";
            case -3:
                return "SW";
            case -4:
                return "S";
            default:
                return "?";
        }
    }

    public final static RouteVariantComparator ROUTE_VARIANT_COMPARATOR = new RouteVariantComparator();
    public final static RouteComparator ROUTE_COMPARATOR = new RouteComparator();
    public final static class RouteComparator implements Comparator<Route> {

        @Override
        public int compare(Route a, Route b) {
            int ret = 0;
            
			ret = this.compareRouteType(a, b);
            if(ret != 0)
                return ret;
            
            return this.compareRouteShortName(a.getShortName(), b.getShortName());
        }

        private int compareRouteType(Route rA, Route rB) {
	        int a = rA.getType(),
			    b = rB.getType();

	        if(a != b) {
				// metró
				if(a == 1) return -1;
				if(b == 1) return  1;

				// hév
				if(a == 2) return -1;
				if(b == 2) return  1;

				// hajó
				if(a == 4) return -1;
				if(b == 4) return  1;
	        }

			// a többi itt van szám alapján rendezve

			// éjszakai
	        if(a == 3 && b == 3) {
		        boolean aNight = rA.getId().getId().startsWith("9"),
		                bNight = rB.getId().getId().startsWith("9");
		        if( aNight && !bNight) return  1;
		        if(!aNight &&  bNight) return -1;
	        }

            // többi egyenlő ~ szám alapján rendeződnek
            return 0;
        }

        private Pattern pattern = Pattern.compile("^(\\D*)(\\d+)");
        
        private int compareRouteShortName(String a, String b) {
            
            Matcher ma = pattern.matcher(a);
            Matcher mb = pattern.matcher(b);
            
            if(ma.find() && mb.find()) {
                int ret;
                if(ma.group(1).length() > 0 && ma.group(1).length() > 0) {
                    ret = ma.group(1).compareTo(mb.group(1));
                    if(ret != 0)
                        return ret;
                } else {
                    if(ma.group(1).length() > 0) return  1;
                    if(mb.group(1).length() > 0) return -1;
                }
                
                int na = Integer.parseInt(ma.group(2));
                int nb = Integer.parseInt(mb.group(2));
            
                ret = na - nb;
                if(ret != 0)
                    return ret;
            }
            
            return a.compareTo(b);
        }
    }

    public final static class RouteVariantComparator implements Comparator<RouteVariant> {
        @Override
        public int compare(RouteVariant o1, RouteVariant o2) {
            return ROUTE_COMPARATOR.compare(o1.getRoute(), o2.getRoute());
        }
    }
}
