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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import org.apache.http.util.TextUtils;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitVehicle;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "vehicles-for-location" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class VehiclesForLocationMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitVehicle>> {

    @QueryParam("query") private String query;
    @QueryParam("lat") private Float lat;
    @QueryParam("lon") private Float lon;
    @QueryParam("latSpan") private Float latSpan;
    @QueryParam("lonSpan") private Float lonSpan;
    @QueryParam("radius") @DefaultValue("100") private int radius;
    @QueryParam("ifModifiedSince") @DefaultValue("-1") long ifModifiedSince;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitVehicle>> getResponse() {
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE);
        
        if(ifModifiedSince >= vehicleLocationService.getLastUpdateTime()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_MODIFIED);
        }

        Collection<VehicleLocation> vehicles;
        
        if(lat != null && lon != null) {
            Coordinate center = new Coordinate(lon, lat);
            Envelope area;
            
            if(lonSpan != null && latSpan != null) {
                Coordinate c1 = new Coordinate(lon - lonSpan, lat - latSpan),
                           c2 = new Coordinate(lon + lonSpan, lat + latSpan);
                
                area = new Envelope(c1, c2);
            } else {
                area = new Envelope(center);
                area.expandBy(radius);
            }
            
            vehicles = vehicleLocationService.getForArea(area);
        } else {
            vehicles = vehicleLocationService.getAll();            
        }

        List<TransitVehicle> transitVehicles = new LinkedList<TransitVehicle>();
        for(VehicleLocation vehicle : vehicles) {
            if(!TextUtils.isEmpty(query)) {
                boolean idMatches = matches(vehicle.getVehicleId().toString()),
                        licencePlaceMatches = matches(vehicle.getLicensePlate()),
                        driverMatches = isInternalRequest() && matches(vehicle.getDriverName()),
                        blockMatches = matches(vehicle.getBlockId()),
                        labelMatches = matches(vehicle.getLabel());
                if(!(idMatches || licencePlaceMatches || driverMatches || blockMatches || labelMatches))
                    continue;
            }

            if(vehicle.getTripId() != null) {
                responseBuilder.addToReferences(getTrip(vehicle.getTripId(), vehicle.getServiceDate()));
            }
            transitVehicles.add(responseBuilder.getVehicle(vehicle));
        }
        
        return responseBuilder.getResponseForList(transitVehicles);
    }

    private boolean matches(String value) {
        return !TextUtils.isEmpty(value) && value.contains(query);
    }
}
