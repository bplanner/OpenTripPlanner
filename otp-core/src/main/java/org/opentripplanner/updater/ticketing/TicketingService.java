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

package org.opentripplanner.updater.ticketing;

import com.sun.org.apache.bcel.internal.util.Objects;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TicketingService {

    private Map<String, TicketingLocation> ticketingLocationsById = new HashMap<String, TicketingLocation>();

    public List<TicketingLocation> getAllLocations() {
        return new ArrayList<TicketingLocation>(ticketingLocationsById.values());
    }

    public List<TicketingLocation> getLocationsNewerThan(Date date) {
        List<TicketingLocation> modified = new ArrayList<TicketingLocation>();

        for (TicketingLocation ticketingLocation : ticketingLocationsById.values()) {
            if (date.before(ticketingLocation.getLastModified())) {
                modified.add(ticketingLocation);
            }
        }

        return modified;
    }

    public void updateLocations(List<TicketingLocation> updatedLocations) {
        Date now = new Date();
        Set<String> missingLocations = new HashSet<String>();
        Map<String, TicketingLocation> modifiedLocations = new HashMap<String, TicketingLocation>();

        for (TicketingLocation location : updatedLocations) {
            String id = location.getId();
            missingLocations.remove(id);
            if (!ticketingLocationsById.containsKey(id) || !Objects.equals(ticketingLocationsById.get(id), location)) {
                modifiedLocations.put(id, location);
            }
        }

        for (String id : missingLocations) {
            modifiedLocations.put(id, TicketingLocation.builder()
                    .id(id)
                    .lastModified(now)
                    .visible(false)
                    .build());
        }

        ticketingLocationsById.putAll(modifiedLocations);
    }
}
