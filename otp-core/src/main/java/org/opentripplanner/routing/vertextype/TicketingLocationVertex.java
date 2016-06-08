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

package org.opentripplanner.routing.vertextype;

import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.graph.AbstractVertex;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.updater.ticketing.TicketingLocation;

/**
 * A vertex for a ticketing location.
 *
 * @author laurent
 */
public class TicketingLocationVertex extends AbstractVertex {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();

    private TicketingLocation location;

    public TicketingLocationVertex(Graph g, TicketingLocation location) {
        super(g, "ticketing place " + location.id, location.lon, location.lat, location.address);
        this.location = location;
    }

    @Override
    public String getName() {
        return location.getAddress() + " (" + location.getOperator() + ")";
    }

    public TicketingLocation getLocation() {
        return location;
    }

    public void setLocation(TicketingLocation location) {
        this.location = location;
    }
}
