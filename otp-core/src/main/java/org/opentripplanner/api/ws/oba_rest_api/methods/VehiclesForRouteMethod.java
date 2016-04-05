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
import org.opentripplanner.api.common.SearchHintService;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitVehicle;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "vehicles-for-route" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class VehiclesForRouteMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitVehicle>> {

    @QueryParam("routeId") private List<String> ids;
    @QueryParam("related") @DefaultValue("false") private boolean related;
    @QueryParam("ifModifiedSince") @DefaultValue("-1") private long ifModifiedSince;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitVehicle>> getResponse() {
        
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE, apiVersion.getApiVersion());
        
        if(ifModifiedSince > 0 && ifModifiedSince >= vehicleLocationService.getLastUpdateTime()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_MODIFIED, apiVersion.getApiVersion());
        }

        SearchHintService searchHintService = graph.getService(SearchHintService.class);
        List<TransitVehicle> transitVehicles = new ArrayList<TransitVehicle>();
        Set<AgencyAndId> routeIds = new HashSet<AgencyAndId>();
        for(String id : ids) {
            AgencyAndId routeId = parseAgencyAndId(id);
            routeIds.add(routeId);

            if(related && searchHintService != null) {
                routeIds.addAll(searchHintService.getHintsForRoute(routeId));
            }
        }

        for(AgencyAndId routeId : routeIds) {
            Route route = transitIndexService.getAllRoutes().get(routeId);
            if(route == null)
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown route.", apiVersion.getApiVersion());
            transitVehicles.addAll(getTransitVehiclesForRoute(vehicleLocationService, routeId));
        }
        
        return responseBuilder.getResponseForList(transitVehicles);
    }
}
