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

import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitScheduleStopTime;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTrip;

import javax.ws.rs.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 */
@Path(OneBusAwayApiMethod.API_BASE_PATH + "arrivals-and-departures-for-location" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class ArrivalsAndDeparturesForLocationOTPMethod extends AbstractArrivalsAndDeparturesOTPMethod<TransitListEntryWithReferences<TransitScheduleStopTime>> {
    
    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitScheduleStopTime>> getResponse() {

        List<Stop> stops = queryStops();

        if(!initRequest())
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, "Date is outside the dateset's validity.");

        List<TransitScheduleStopTime> stopTimes = new LinkedList<TransitScheduleStopTime>();
        Set<TransitTrip> trips = new HashSet<TransitTrip>();

        boolean limitExceeded = getResponse(stops, false, stopTimes, trips);

        for(TransitTrip trip : trips) {
            responseBuilder.addToReferences(trip);
        }

        return responseBuilder.getResponseForList(limitExceeded, stopTimes);
    }
}
