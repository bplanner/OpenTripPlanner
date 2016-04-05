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
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitVehicle;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.updater.vehicle_location.VehicleLocationService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "vehicle-for-trip" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class VehicleForTripMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitVehicle>> {

    @QueryParam("tripId") private List<String> ids = new LinkedList<String>();
	@QueryParam("date") private List<String> dates = new LinkedList<String>();
    @QueryParam("ifModifiedSince") @DefaultValue("-1") private long ifModifiedSince;
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitVehicle>> getResponse() {
        VehicleLocationService vehicleLocationService = graph.getService(VehicleLocationService.class);
        if(vehicleLocationService == null)
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_VEHICLE_LOCATION_SERVICE, apiVersion.getApiVersion());
        
        if(ifModifiedSince > 0 && ifModifiedSince >= vehicleLocationService.getLastUpdateTime()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_MODIFIED, apiVersion.getApiVersion());
        }

		List<ServiceDate> serviceDates = new ArrayList<ServiceDate>();
		List<TransitVehicle> transitVehicles = new ArrayList<TransitVehicle>();

		if(dates.isEmpty()) {
			for(String ignored : ids) {
				serviceDates.add(new ServiceDate());
			}
		} else if(dates.size() != ids.size()) {
			return TransitResponseBuilder.getFailResponse(TransitResponse.Status.INVALID_VALUE, "If dates are provided tripId and date parameter count must match.", apiVersion.getApiVersion());
		} else {
			try {
				for(String date : dates) {
					serviceDates.add(ServiceDate.parseString(date));
				}
			} catch (ParseException e) {
				return TransitResponseBuilder.getFailResponse(TransitResponse.Status.INVALID_VALUE, "Failed to parse date.", apiVersion.getApiVersion());
			}
		}

		for(String id : ids) {
			AgencyAndId tripId = parseAgencyAndId(id);
			ServiceDate serviceDate = serviceDates.remove(0);
			Trip trip = getTrip(tripId, serviceDate);
			if(trip == null) {
				return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown trip.", apiVersion.getApiVersion());
			}

            if(!isInternalRequest() && GtfsLibrary.isAgencyInternal(trip)) {
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown trip.", apiVersion.getApiVersion());
            }

			TransitVehicle transitVehicle = getTransitVehicleForTrip(vehicleLocationService, tripId, serviceDate);
			if(transitVehicle != null)
				transitVehicles.add(transitVehicle);
		}

        return responseBuilder.getResponseForList(transitVehicles);
    }
}
