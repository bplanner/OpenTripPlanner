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
import org.onebusaway.gtfs.model.Route;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTripDetails;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/trips-for-route.html">trips-for-route</a> OneBusAway API method. Only trips with realtime vehicles are returned.
 */

@Path(OneBusAwayApiMethod.API_BASE_PATH + "trips-for-route/{routeId}" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class TripsForRouteMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitTripDetails>> {

    @PathParam("routeId") private String routeIdString;
    @QueryParam("includeTrips") @DefaultValue("false") private boolean includeTrips;
    @QueryParam("includeSchedules") @DefaultValue("false") private boolean includeSchedules;
    @QueryParam("includeStatus") @DefaultValue("true") private boolean includeStatus;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitTripDetails>> getResponse() {
        AgencyAndId routeId = parseAgencyAndId(routeIdString);
        
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE, apiVersion.getApiVersion());
        
        Route route = transitIndexService.getAllRoutes().get(routeId);
        if(route == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown route.", apiVersion.getApiVersion());

        Collection<VehicleLocation> vehicles = vehicleLocationService.getForRoute(routeId);
        
        List<TransitTripDetails> tripDetails = new ArrayList<TransitTripDetails>(vehicles.size());
        for(VehicleLocation vehicle : vehicles) {
            TransitTripDetails tripDetail = getTripDetails(vehicle.getTripId(), vehicle.getServiceDate(),
                    includeStatus, includeSchedules, includeTrips);
            
            if(tripDetail != null) {
                tripDetails.add(tripDetail);
            }
        }
        
        return responseBuilder.getResponseForList(tripDetails);
    }
}
