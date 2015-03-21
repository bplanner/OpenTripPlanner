package org.opentripplanner.api.ws.oba_rest_api.methods;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.InjectParam;
import com.sun.jersey.api.spring.Autowire;
import lombok.Setter;
import org.codehaus.jettison.json.JSONException;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.api.common.Message;
import org.opentripplanner.api.common.RoutingResource;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.error.PlannerError;
import org.opentripplanner.api.ws.PlanGenerator;
import org.opentripplanner.api.ws.Response;
import org.opentripplanner.api.ws.oba_rest_api.OneBusAwayApiCacheService;
import org.opentripplanner.api.ws.oba_rest_api.OneBusAwayRequestLogger;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.TableTripPattern;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.services.TransitIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.EnumMap;
import java.util.Map;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/agency.html">plan-trip</a> OneBusAway API method. Is in no way compatible with the original method.
 */

@Autowire
@Produces({ MediaType.APPLICATION_JSON })
@Path(OneBusAwayApiMethod.API_BASE_PATH + "plan-trip" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class PlanTripMethod extends RoutingResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlanTripMethod.class);
    private OneBusAwayRequestLogger requestLogger = new OneBusAwayRequestLogger();

    @Setter @InjectParam 
    public PlanGenerator planGenerator;
    
    // We inject info about the incoming request so we can include the incoming query 
    // parameters in the outgoing response. This is a TriMet requirement.
    // Jersey seems to use @Context to inject internal types and @InjectParam or @Resource for DI objects.
    @Context UriInfo uriInfo;

    /** Java is immensely painful. TODO: Guava should cover this. */
    interface OneArgFunc<T,U> {
        public T call(U arg);
    }

    private static final Map<Message, TransitResponse.Status> errorMessageStatus = new EnumMap<Message, TransitResponse.Status>(Message.class);
    static {
        errorMessageStatus.put(Message.BOGUS_PARAMETER, TransitResponse.Status.BOGUS_PARAMETER);
        errorMessageStatus.put(Message.LOCATION_NOT_ACCESSIBLE, TransitResponse.Status.LOCATION_NOT_ACCESSIBLE);
        errorMessageStatus.put(Message.NO_TRANSIT_TIMES, TransitResponse.Status.NO_TRANSIT_TIMES);
        errorMessageStatus.put(Message.OUTSIDE_BOUNDS, TransitResponse.Status.OUTSIDE_BOUNDS);
        errorMessageStatus.put(Message.PATH_NOT_FOUND, TransitResponse.Status.PATH_NOT_FOUND);
        errorMessageStatus.put(Message.PLAN_OK, TransitResponse.Status.OK);
        errorMessageStatus.put(Message.REQUEST_TIMEOUT, TransitResponse.Status.REQUEST_TIMEOUT);
        errorMessageStatus.put(Message.SYSTEM_ERROR, TransitResponse.Status.UNKNOWN_ERROR);
        errorMessageStatus.put(Message.TOO_CLOSE, TransitResponse.Status.TOO_CLOSE);
    }

	@QueryParam("key") protected String apiKey;
	@QueryParam("appVersion") protected String appVersion;
	@PathParam("dialect") protected TransitResponseBuilder.DialectWrapper dialect;
    @QueryParam("routerId") private String routerId;
    @QueryParam("includeReferences") @DefaultValue("true") private TransitResponseBuilder.ReferencesWrapper references;
	@HeaderParam("X-BKK-Internal-Request") @DefaultValue("false") private boolean internalRequest;

	@CookieParam("_ga") private String clientId;
	/*@Setter @InjectParam("GoogleAnalyticsId")*/ private String googleAnalyticsId;

    @Context private HttpContext httpContext;

    @GET
    public javax.ws.rs.core.Response plan() {
	    OneBusAwayRequestLogger.LogRequest logRequest
            = requestLogger.startRequest(this, httpContext, uriInfo.getRequestUri(), clientId, apiKey, appVersion, internalRequest, dialect);

        Graph graph = getGraph(routerId);
        if(graph == null) {
            return TransitResponseBuilder.getWsResponse(TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_NO_GRAPH));
        }

        TransitIndexService transitIndexService = graph.getService(TransitIndexService.class);
        if (transitIndexService == null) {
            return TransitResponseBuilder.getWsResponse(TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_TRANSIT_INDEX_SERVICE));
        }
        
        OneBusAwayApiCacheService cacheService = graph.getService(OneBusAwayApiCacheService.class);
        if(cacheService == null) {
            cacheService = new OneBusAwayApiCacheService();
            graph.putService(OneBusAwayApiCacheService.class, cacheService);
        }

	    if(!optimize.isEmpty() && optimize.get(0) == OptimizeType.WALK) {
		    optimize.set(0, OptimizeType.QUICK);
		    if(walkReluctance.isEmpty()) {
			    walkReluctance.add(6.0);
		    }
	    }
	    else if(!optimize.isEmpty() && optimize.get(0) == OptimizeType.TRANSFERS) {
		    optimize.set(0, OptimizeType.QUICK);
		    if(transferPenalty.get(0) < 0) {
				transferPenalty.set(0, 900);
		    }
	    }

		if(!modes.isEmpty() && modes.get(0).contains(TraverseMode.BICYCLE)) {
			this.maxWalkDistance.add(0, Double.MAX_VALUE);
		}

        if(maxHours.isEmpty()) {
            maxHours.add(5);
        }

        try {
            TransitResponseBuilder builder = new TransitResponseBuilder(graph, references.getReferences(), dialect.getDialect(), internalRequest, httpContext.getRequest());
            Response plan = getItineraries();
            
            if(plan.getError() != null) {
                PlannerError error = plan.getError();
                TransitResponse.Status status = TransitResponse.Status.UNKNOWN_ERROR;
                if(error.getMessage() != null && errorMessageStatus.containsKey(error.getMessage())) {
                    status = errorMessageStatus.get(error.getMessage());
                }
                TransitResponse<TransitEntryWithReferences<Response>> response = builder.getResponseForErrorTripPlan(status, plan);
                logRequest.finishRequest(response);
                return TransitResponseBuilder.getWsResponse(response);
            }

            TripPlan tripPlan = plan.getPlan();
            if(tripPlan.itinerary != null) {
                for(Itinerary itinerary : tripPlan.itinerary) {
                    if(itinerary.legs == null) continue;

                    for(Leg leg : itinerary.legs) {
                        if(leg.tripId != null) {
                            AgencyAndId tripId = new AgencyAndId(leg.agencyId, leg.tripId);
                            Trip trip = getTrip(transitIndexService, tripId, ServiceDate.parseString(leg.serviceDate));
                            leg.tripId = tripId.toString();
                            builder.addToReferences(trip);
                        }
                        if(leg.routeId != null) {
                            AgencyAndId routeId = new AgencyAndId(leg.agencyId, leg.routeId);
                            Route route = transitIndexService.getAllRoutes().get(routeId);
                            leg.routeId = routeId.toString();
                            builder.addToReferences(route);
                        }
                        if(leg.from.stopId != null) {
                            Stop stop = transitIndexService.getAllStops().get(AgencyAndId.convertFromString(leg.from.stopId));
                            builder.addToReferences(stop);
                        }
                        if(leg.to.stopId != null) {
                            Stop stop = transitIndexService.getAllStops().get(AgencyAndId.convertFromString(leg.to.stopId));
                            builder.addToReferences(stop);
                        }
                        if(leg.alerts != null) {
                            for(Alert alert : leg.alerts) {
                                if(alert.alertId != null)
                                    builder.addToReferences(alert);
                            }
                        }
                        if(leg.stop != null) {
                            for(Place place : leg.stop) {
                                Stop stop = transitIndexService.getAllStops().get(AgencyAndId.convertFromString(place.stopId));
                                builder.addToReferences(stop);
                            }
                        }
                    }
                }
            }

            TransitResponse<TransitEntryWithReferences<Response>> response = builder.getResponseForTripPlan(plan);
	        logRequest.finishRequest(response);
            return TransitResponseBuilder.getWsResponse(response);
        } catch(Exception e) {
            LOG.warn("Trip Planning Exception: ", e);
            TransitResponse<TransitEntryWithReferences<Response>> response = TransitResponseBuilder.<TransitEntryWithReferences<Response>>getFailResponse(TransitResponse.Status.UNKNOWN_ERROR, "An error occured: " + e.getClass().getName());
            logRequest.exception(response, e);
            return TransitResponseBuilder.getWsResponse(response);
        }
    }
    
    private Trip getTrip(TransitIndexService transitIndexService, AgencyAndId tripId, ServiceDate serviceDate) {
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        Trip trip = pattern.getTrip(tripId);
        // TODO: wheelchair accessibility
        return trip;
    }
    
    private Graph getGraph(String routerId) {
        try {
            return graphService.getGraph(routerId);
        } catch(Exception e) {
            return null;
        }
    }
    
    private Response wrapGenerate(OneArgFunc<TripPlan, RoutingRequest> func) {

        /*
         * TODO: add Lang / Locale parameter, and thus get localized content (Messages & more...)
         * TODO: from/to inputs should be converted / geocoded / etc... here, and maybe send coords 
         *       or vertex ids to planner (or error back to user)
         * TODO: org.opentripplanner.routing.impl.PathServiceImpl has COOORD parsing. Abstract that
         *       out so it's used here too...
         */

        // create response object, containing a copy of all request parameters
        Response response = new Response(uriInfo);
        RoutingRequest request = null;
        try {
            // fill in request from query parameters via shared superclass method
            request = super.buildRequest();
            request.internalRequest = internalRequest;
            TripPlan plan = func.call(request);
            response.setPlan(plan);
        } catch (Exception e) {
            PlannerError error = new PlannerError(e);
            if(!PlannerError.isPlanningError(e.getClass()))
                LOG.warn("Error while planning path: ", e);
            response.setError(error);
        } finally {
            if (request != null) {
                response.debug = request.rctx.debug;
                request.cleanup(); // TODO verify that this is being done on Analyst web services
            }       
        }
        return response;
    }

    public Response getItineraries() throws JSONException {
        return wrapGenerate(new OneArgFunc<TripPlan, RoutingRequest>() {
            @Override
            public TripPlan call(RoutingRequest request) {
                return planGenerator.generate(request);
            }});
    }
}
