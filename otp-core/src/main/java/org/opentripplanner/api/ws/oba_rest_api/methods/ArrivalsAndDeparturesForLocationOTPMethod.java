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
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitDepartureGroup;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitScheduleStopTime;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitStop;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTrip;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
@Path(OneBusAwayApiMethod.API_BASE_PATH + "arrivals-and-departures-for-location" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class ArrivalsAndDeparturesForLocationOTPMethod extends AbstractArrivalsAndDeparturesOTPMethod<TransitListEntryWithReferences<TransitDepartureGroup>> {

    @QueryParam("groupLimit")
    @DefaultValue("4")
    protected int groupLimit;
    @QueryParam("clientLon")
    @DefaultValue("0")
    protected double clientLon;
    @QueryParam("clientLat")
    @DefaultValue("0")
    protected double clientLat;

    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitDepartureGroup>> getResponse() {

        List<Stop> stops = queryStops();

        if (!initRequest()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, "Date is outside the dateset's validity.", apiVersion.getApiVersion());
        }

        if (!parseRoutes()) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown routeId.", apiVersion.getApiVersion());
        }

        List<TransitScheduleStopTime> stopTimes = new LinkedList<TransitScheduleStopTime>();
        Set<TransitTrip> trips = new HashSet<TransitTrip>();

        boolean limitExceeded = getResponse(stops, false, stopTimes, trips);

        Map<String, TransitTrip> tripsById = new HashMap<String, TransitTrip>();
        for (TransitTrip trip : trips)
            tripsById.put(trip.getId(), trip);

        Map<String, TransitDepartureGroup> groups = new HashMap<String, TransitDepartureGroup>();
        for (TransitScheduleStopTime stopTime : stopTimes) {
            TransitTrip trip = tripsById.get(stopTime.getTripId());
            String key = trip.getRouteId() + "-" + trip.getTripHeadsign();

            TransitDepartureGroup group = groups.get(key);
            if (group == null) {
                group = new TransitDepartureGroup();
                group.setRouteId(trip.getRouteId());
                group.setHeadsign(trip.getTripHeadsign());
            }

            boolean contains = false,
                    overrides = false;
            int containedIndex = -1;

            for (int i = group.getStopTimes().size() - 1; i >= 0; i--) {
                TransitScheduleStopTime groupStopTime = group.getStopTimes().get(i);
                if (groupStopTime.getTripId().equals(stopTime.getTripId())) {
                    TransitStop groupStop = responseBuilder.getReferences().getStops().get(stopTime.getStopId()),
                            stop = responseBuilder.getReferences().getStops().get(stopTime.getStopId());
                    double distA = SphericalDistanceLibrary.getInstance().fastDistance(clientLat, clientLon, groupStop.getLat(), groupStop.getLon()),
                            distB = SphericalDistanceLibrary.getInstance().fastDistance(clientLat, clientLon, stop.getLat(), stop.getLon());
                    contains = true;

                    if (distA > distB) {
                        overrides = true;
                        containedIndex = i;
                    }
                }
            }

            if (contains && overrides) {
                group.getStopTimes().set(containedIndex, stopTime);
            } else if (!contains && group.getStopTimes().size() < groupLimit) {
                group.getStopTimes().add(stopTime);
                responseBuilder.addToReferences(trip);
                groups.put(key, group);
            }
        }

        List<TransitDepartureGroup> sortedGroups = new ArrayList<TransitDepartureGroup>(groups.values());
        Collections.sort(sortedGroups, new GroupSorter());

        for (TransitDepartureGroup group : sortedGroups)
            Collections.sort(group.getStopTimes());

        return responseBuilder.getResponseForList(limitExceeded, sortedGroups);
    }

    private static class GroupSorter implements Comparator<TransitDepartureGroup> {

        @Override
        public int compare(TransitDepartureGroup t1, TransitDepartureGroup t2) {
            return t1.getStopTimes().get(0).compareTo(t2.getStopTimes().get(0));
        }
    }
}
