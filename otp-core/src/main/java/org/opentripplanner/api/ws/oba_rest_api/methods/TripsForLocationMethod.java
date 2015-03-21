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
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTripDetails;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/trips-for-location.html">trips-for-location</a> OneBusAway API method. Only trips with realtime vehicles are returned.
 */

@Path(OneBusAwayApiMethod.API_BASE_PATH + "trips-for-location" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class TripsForLocationMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitTripDetails>> {
    
    @QueryParam("lat") private Float lat;
    @QueryParam("lon") private Float lon;
    @QueryParam("latSpan") private Float latSpan;
    @QueryParam("lonSpan") private Float lonSpan;
    @QueryParam("radius") @DefaultValue("100") private int radius;
    @QueryParam("includeTrips") @DefaultValue("false") private boolean includeTrips;
    @QueryParam("includeSchedules") @DefaultValue("false") private boolean includeSchedules;
    @QueryParam("includeStatus") @DefaultValue("true") private boolean includeStatus;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitTripDetails>> getResponse() {

        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE);

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
        
        List<TransitTripDetails> tripDetails = new ArrayList<TransitTripDetails>(vehicles.size());
        for(VehicleLocation vehicle : vehicles) {
            AgencyAndId tripId = vehicle.getTripId();
            if (tripId != null) {
                TransitTripDetails tripDetail = getTripDetails(tripId, vehicle.getServiceDate(),
                        includeStatus, includeSchedules, includeTrips);
                
                if(tripDetail != null) {
                    tripDetails.add(tripDetail);
                }
            }
        }
        
        return responseBuilder.getResponseForList(tripDetails);
    }
}
