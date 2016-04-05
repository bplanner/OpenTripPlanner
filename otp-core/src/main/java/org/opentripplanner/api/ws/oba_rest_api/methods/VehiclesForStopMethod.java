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
import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitVehicle;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "vehicles-for-stop" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class VehiclesForStopMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitVehicle>> {

    @QueryParam("stopId") private String id;
    @QueryParam("ifModifiedSince") @DefaultValue("-1") private long ifModifiedSince;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitVehicle>> getResponse() {
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE, apiVersion.getApiVersion());
        
        if(ifModifiedSince > 0 && ifModifiedSince >= vehicleLocationService.getLastUpdateTime()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_MODIFIED, apiVersion.getApiVersion());
        }

        AgencyAndId stopId = parseAgencyAndId(id);
        Stop stop = transitIndexService.getAllStops().get(stopId);
        if(stop == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown stop.", apiVersion.getApiVersion());
        
        responseBuilder.addToReferences(stop);

        List<TransitVehicle> transitVehicles = new ArrayList<TransitVehicle>();
        for(AgencyAndId routeId : getRoutesForStop(stopId)) {
            transitVehicles.addAll(getTransitVehiclesForRoute(vehicleLocationService, routeId));
        }
        
        return responseBuilder.getResponseForList(transitVehicles);
    }
}
