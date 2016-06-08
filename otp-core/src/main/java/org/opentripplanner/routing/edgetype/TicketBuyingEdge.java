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

package org.opentripplanner.routing.edgetype;

import com.vividsolutions.jts.geom.LineString;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.vertextype.TicketingLocationVertex;
import org.opentripplanner.updater.ticketing.TicketingLocation;

import java.util.Date;
import java.util.Map;

/**
 * Renting or dropping off a rented bike edge.
 *
 * @author laurent
 */
public class TicketBuyingEdge extends Edge {

    private static final long serialVersionUID = 1L;

    private final Map<TicketingLocation.DayOfWeek, AgencyAndId> serviceIds;

    public TicketBuyingEdge(Map<TicketingLocation.DayOfWeek, AgencyAndId> serviceIds, Vertex from, Vertex to) {
        super(from, to);
        this.serviceIds = serviceIds;
    }

    @Override
    public State traverse(State s0) {
        RoutingRequest options = s0.getOptions();
        if (!options.shouldBuyTickets) {
            return null;
        }

        int waitToOpen = timeUntilOpening(s0.getOptions().isArriveBy(), s0.getTimeInMillis(), options.getRctx().graph);
        if (waitToOpen < 0) {
            return null;
        }

        if (options.isArriveBy()) {
            if (!s0.boughtTicket()) {
                return null;
            }
        } else {
            if (s0.boughtTicket()) {
                return null;
            }
        }

        StateEditor s1 = s0.edit(this);
        s1.incrementWeight(options.ticketBuyingCost + waitToOpen * options.waitReluctance);
        s1.incrementTimeInSeconds(options.ticketBuyingTime + waitToOpen);
        s1.setBoughtTicket(!options.isArriveBy());
        s1.setBackMode(TraverseMode.LEG_SWITCH);
        State s1b = s1.makeState();
        return s1b;
    }

    private int timeUntilOpening(boolean isArriveBy, long timeInMillis, Graph graph) {
        Date date = new Date(timeInMillis);
        int waitSeconds = -1;

        for (TicketingLocation.TicketingPeriod period : getTicketingLocation().getOpeningPeriods()) {
            int periodSeconds = timeUntilOpening(isArriveBy, period, date, graph);
            if (periodSeconds >= 0 && (waitSeconds < 0 || periodSeconds < waitSeconds)) {
                waitSeconds = periodSeconds;
            }
        }

        return waitSeconds;
    }

    private int timeUntilOpening(boolean isArriveBy, TicketingLocation.TicketingPeriod period, Date date, Graph graph) {
        if (period.getDayOfWeek() == TicketingLocation.DayOfWeek.O247) {
            return 0;
        }

        ServiceDate serviceDate = new ServiceDate(date);
        CalendarService calendarService = graph.getCalendarService();
        if (!serviceIds.containsKey(period.getDayOfWeek()) || !calendarService.getServiceIdsOnDate(serviceDate).contains(serviceIds.get(period.getDayOfWeek()))) {
            return -1;
        }

        Date startOfDay = serviceDate.getAsDate(graph.getTimeZone());
        int daySeconds = (int) ((date.getTime() - startOfDay.getTime()) / 1000);

        if(!isArriveBy) {
            if (daySeconds >= period.getClosesSeconds()) {
                return -1;
            } else if (daySeconds >= period.getOpensSeconds()) {
                return 0;
            } else {
                return period.getOpensSeconds() - daySeconds;
            }
        } else {
            if (daySeconds <= period.getOpensSeconds()) {
                return -1;
            } else if (daySeconds <= period.getClosesSeconds()) {
                return 0;
            } else {
                return daySeconds - period.getClosesSeconds();
            }
        }
    }

    @Override
    public double getDistance() {
        return 0;
    }

    @Override
    public LineString getGeometry() {
        return null;
    }

    @Override
    public String getName() {
        return getToVertex().getName();
    }

    @Override
    public boolean hasBogusName() {
        return false;
    }

    public TicketingLocation getTicketingLocation() {
        return ((TicketingLocationVertex) fromv).getLocation();
    }
}
