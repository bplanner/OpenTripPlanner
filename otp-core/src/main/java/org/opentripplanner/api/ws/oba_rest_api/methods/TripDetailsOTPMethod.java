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

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitStopTime;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTrip;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTripDetailsOTP;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitVehicle;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.ServiceDay;
import org.opentripplanner.routing.edgetype.TableTripPattern;
import org.opentripplanner.routing.transit_index.RouteVariant;
import org.opentripplanner.routing.trippattern.CanceledTripTimes;
import org.opentripplanner.routing.trippattern.TripTimes;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.text.ParseException;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "trip-details" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class TripDetailsOTPMethod extends OneBusAwayApiMethod<TransitEntryWithReferences<TransitTripDetailsOTP>> {

    @QueryParam("vehicleId") private String vehicleIdString;
    @QueryParam("tripId") private String tripIdString;
    @QueryParam("date") private String date;
    
    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitTripDetailsOTP>> getResponse() {
        ServiceDate serviceDate;
        AgencyAndId tripId;
        
        if(vehicleIdString != null) {
            VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
            AgencyAndId vehicleId = parseAgencyAndId(vehicleIdString);

            if(vehicleLocationService == null)
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE, apiVersion.getApiVersion());

            VehicleLocation vehicle = vehicleLocationService.getForVehicle(vehicleId);
            if(vehicle == null)
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "A vehicle of the given id doesn't exist.", apiVersion.getApiVersion());
            
            serviceDate = vehicle.getServiceDate();
            tripId = vehicle.getTripId();
        } else {
            serviceDate = new ServiceDate();
            if(date != null) {
                try {
                    serviceDate = ServiceDate.parseString(date);
                } catch (ParseException ex) {
                    return TransitResponseBuilder.getFailResponse(TransitResponse.Status.INVALID_VALUE, "Failed to parse service date.", apiVersion.getApiVersion());
                }
            }

            tripId = parseAgencyAndId(tripIdString);
            if(transitIndexService.getTripPatternForTrip(tripId, serviceDate) == null)
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown tripId.", apiVersion.getApiVersion());
        }
            
        Trip trip = getTrip(tripId, serviceDate);
        if(!isInternalRequest() && GtfsLibrary.isAgencyInternal(trip)) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown tripId.", apiVersion.getApiVersion());
        }
        
        CalendarService calendarService = graph.getCalendarService();
        ServiceDay serviceDay = new ServiceDay(graph, serviceDate, calendarService, trip.getId().getAgencyId());
        
        long startTime = serviceDate.getAsDate(graph.getTimeZone()).getTime() / 1000;
        long endTime = serviceDate.next().getAsDate(graph.getTimeZone()).getTime() / 1000 - 1;
        
        if(!graph.transitFeedCovers(startTime) && graph.transitFeedCovers(endTime)) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, "Date is outside the dateset's validity.", apiVersion.getApiVersion());
        }
        
        RoutingRequest options = makeTraverseOptions(startTime, routerId);
        
        RouteVariant variant = transitIndexService.getVariantForTrip(tripId);
        
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        if(!serviceDay.serviceIdRunning(pattern.getServiceId()))
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_OPERATING, "Trip isn't in operation on the given service date.", apiVersion.getApiVersion());
        
        TripTimes tripTimes = getTripTimesForTrip(tripId, serviceDate);
        if(tripTimes instanceof CanceledTripTimes)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_OPERATING, "Trip is canceled on the given service date.", apiVersion.getApiVersion());
        
        List<TransitStopTime> stopTimes = getStopTimesForTrip(serviceDate, pattern, tripTimes);

		if(!stopTimes.isEmpty()) {
			TransitStopTime first = stopTimes.get(0);
			TransitStopTime last = stopTimes.get(stopTimes.size() - 1);

			if(first.hasDepartureTime())
				startTime = first.getDepartureTime();
			else if(first.hasPredictedDepartureTime())
				startTime = first.getPredictedDepartureTime();

			if(last.hasPredictedArrivalTime())
				endTime = last.getPredictedArrivalTime();
			else if(last.hasArrivalTime())
				endTime = last.getArrivalTime();
		}

        TransitVehicle transitVehicle = null;
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService != null) {
            VehicleLocation vehicle = vehicleLocationService.getForTrip(tripId);
            if(vehicle != null && vehicle.getServiceDate().equals(serviceDate)) {
                transitVehicle = responseBuilder.getVehicle(vehicle);
                setDelayForVehicle(vehicle, transitVehicle);
            }
        }
        
        TransitTrip transitTrip = responseBuilder.getTrip(trip);
        transitTrip.setWheelchairAccessible(tripTimes.isWheelchairAccessible());
        
        AgencyAndId routeId = trip.getRoute().getId();
        List<String> alertIds = getAlertsForTrip(tripId, routeId, options, startTime, endTime);
                
        return responseBuilder.getResponseForTrip(transitTrip, serviceDate, alertIds, pattern.getStops(), stopTimes, transitVehicle, variant);
    }
}
