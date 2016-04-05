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
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitArrivalsAndDepartures;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitScheduleStopTime;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTrip;
import org.opentripplanner.routing.core.RoutingRequest;

import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 */
@Path(OneBusAwayApiMethod.API_BASE_PATH + "arrivals-and-departures-for-stop" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class ArrivalsAndDeparturesForStopOTPMethod extends AbstractArrivalsAndDeparturesOTPMethod<TransitEntryWithReferences<TransitArrivalsAndDepartures>> {
    
    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitArrivalsAndDepartures>> getResponse() {

        boolean single;
        List<Stop> stops;

        if(stopIdStrings != null && !stopIdStrings.isEmpty()) {
            stops = new LinkedList<Stop>();
            single = stopIdStrings.size() == 1;

            for (String stopIdString : stopIdStrings) {
                AgencyAndId stopId = parseAgencyAndId(stopIdString);
                Stop stop = transitIndexService.getAllStops().get(stopId);
                if (stop == null) {
                    return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown stopId.", apiVersion.getApiVersion());
                } else {
                    stops.add(stop);
                }
            }
        } else {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown stopId.", apiVersion.getApiVersion());
        }

        if(!initRequest()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, "Date is outside the dateset's validity.",
                    responseBuilder.entity(responseBuilder.getArrivalsAndDepartures(stops.get(0), null, null, null, null)), apiVersion.getApiVersion());
        }

        List<TransitScheduleStopTime> stopTimes = new LinkedList<TransitScheduleStopTime>();
        Set<TransitTrip> trips = new HashSet<TransitTrip>();
        Set<String> nearbyStopIds = new HashSet<String>();
        Set<String> alertIds = new HashSet<String>();

        RoutingRequest options = makeTraverseOptions(startTime, routerId);

        boolean limitExceeded = getResponse(stops, single, stopTimes, trips);

        for(Stop stop : stops) {
            if(single)
                nearbyStopIds.addAll(getNearbyStops(stop));

            alertIds.addAll(getAlertsForStop(stop.getId(), options, startTime, endTime));
        }

        return responseBuilder.getResponseForStop(stops.get(0), limitExceeded, stopTimes, new ArrayList<String>(alertIds), new ArrayList<TransitTrip>(trips), new ArrayList<String>(nearbyStopIds));
    }
}
