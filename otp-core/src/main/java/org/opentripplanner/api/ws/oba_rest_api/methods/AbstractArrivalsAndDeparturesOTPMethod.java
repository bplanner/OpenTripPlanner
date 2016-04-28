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
import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitScheduleStopTime;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTrip;
import org.opentripplanner.common.model.T2;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractArrivalsAndDeparturesOTPMethod<T> extends AbstractLocationSearchMethod<T> {

    private static final int MAX_LIMIT = 200;
    private static final int MAX_MINUTES = 360;

    @QueryParam("minutesBefore")
    @DefaultValue("2")
    protected int minutesBefore;
    @QueryParam("minutesAfter")
    @DefaultValue("30")
    protected int minutesAfter;
    @QueryParam("stopId")
    protected List<String> stopIdStrings;
    @QueryParam("includeRouteId")
    protected List<String> includeRouteIdStrings;
    @QueryParam("time")
    protected Long time;
    @QueryParam("onlyDepartures")
    @DefaultValue("true")
    protected boolean onlyDepartures;
    @QueryParam("limit")
    @DefaultValue("60")
    protected int limit;

    protected long startTime;
    protected long endTime;

    protected boolean initRequest() {
        if (time == null)
            time = System.currentTimeMillis() / 1000;
        startTime = time - minutesBefore * 60;
        endTime = time + minutesAfter * 60;

        return graph.transitFeedCovers(startTime) && graph.transitFeedCovers(endTime);
    }

    protected boolean parseRoutes() {
        if (includeRouteIdStrings != null && !includeRouteIdStrings.isEmpty()) {
            for (String routeIdString : includeRouteIdStrings) {
                Route route = transitIndexService.getAllRoutes().get(AgencyAndId.convertFromString(routeIdString));
                if (route == null) {
                    return false;
                }
            }
        }

        return true;
    }

    protected boolean getResponse(List<Stop> stops, boolean single, List<TransitScheduleStopTime> stopTimes, Set<TransitTrip> trips) {
        List<T2<TransitScheduleStopTime, TransitTrip>> stopTimesWithTrips = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>();
        boolean limitExceeded = false;

        if (endTime < startTime || endTime - startTime > 60 * MAX_MINUTES) {
            return true;
        }

        if (limit > MAX_LIMIT) {
            return true;
        }

        for (Stop stop : stops) {
            String stopId = stop.getId().toString();
            for (T2<TransitScheduleStopTime, TransitTrip> stopTimeT2 : getStopTimesForStop(startTime, endTime, stop.getId(), onlyDepartures, stopIdStrings.size() > 1, limit)) {
                if (!single) {
                    stopTimeT2.getFirst().setStopId(stopId);
                }
                stopTimesWithTrips.add(stopTimeT2);
            }

            responseBuilder.addToReferences(stop);
        }

        sortStopTimesWithTrips(stopTimesWithTrips);

        List<T2<TransitScheduleStopTime, TransitTrip>> filteredStopTimesWithTrips = new ArrayList<T2<TransitScheduleStopTime, TransitTrip>>();
        Set<String> seenHeadsigns = new HashSet<String>();
        for(String routeId : includeRouteIdStrings) {
            for(int i = 0; i < stopTimesWithTrips.size(); ++i) {
                TransitTrip transitTrip = stopTimesWithTrips.get(i).getSecond();
                if(Objects.equals(routeId, transitTrip.getRouteId()) && !seenHeadsigns.contains(transitTrip.getTripHeadsign())) {
                    filteredStopTimesWithTrips.add(stopTimesWithTrips.remove(i));
                    seenHeadsigns.add(transitTrip.getTripHeadsign());
                    i--;
                }
            }
            seenHeadsigns.clear();
        }

        if (stopTimesWithTrips.size() > limit && limit > 0) {
            filteredStopTimesWithTrips.addAll(stopTimesWithTrips.subList(0, limit));
            limitExceeded = true;
        } else {
            filteredStopTimesWithTrips.addAll(stopTimesWithTrips);
        }

        sortStopTimesWithTrips(filteredStopTimesWithTrips);

        for (T2<TransitScheduleStopTime, TransitTrip> stopTimeWithTrip : filteredStopTimesWithTrips) {
            stopTimes.add(stopTimeWithTrip.getFirst());
            trips.add(stopTimeWithTrip.getSecond());
        }

        return limitExceeded;
    }
}
