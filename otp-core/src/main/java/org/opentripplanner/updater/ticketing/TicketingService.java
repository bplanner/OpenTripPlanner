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
import org.onebusaway.gtfs.model.AgencyAndId;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TicketingService {

    private final Map<TicketingLocation.DayOfWeek, AgencyAndId> serviceIds;
    private Map<String, TicketingLocation> ticketingLocationsById = new HashMap<String, TicketingLocation>();
    private Map<String, TicketingProduct> ticketingProductsById = new HashMap<String, TicketingProduct>();

    public TicketingService(Map<TicketingLocation.DayOfWeek, AgencyAndId> serviceIds) {
        this.serviceIds = serviceIds;
    }

    public List<TicketingLocation> getAllLocations() {
        return new ArrayList<TicketingLocation>(ticketingLocationsById.values());
    }

    public List<TicketingProduct> getAllProducts() {
        return new ArrayList<TicketingProduct>(ticketingProductsById.values());
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

    public List<TicketingProduct> getProductsNewerThan(Date date) {
        List<TicketingProduct> modified = new ArrayList<TicketingProduct>();

        for (TicketingProduct ticketingProduct : ticketingProductsById.values()) {
            if (date.before(ticketingProduct.getLastModified())) {
                modified.add(ticketingProduct);
            }
        }

        return modified;
    }

    public TicketingLocation addLocation(TicketingLocation location) {
        String id = location.getId();
        if (!ticketingLocationsById.containsKey(id) || !Objects.equals(ticketingLocationsById.get(id), location)) {
            ticketingLocationsById.put(location.id, location);
        }
        return location;
    }

    public void removeLocation(TicketingLocation location) {
        ticketingLocationsById.remove(location.id);
    }

    public void updateProducts(List<TicketingProduct> products) {
        Set<String> existingIds = new HashSet<String>(ticketingProductsById.keySet());
        for (TicketingProduct product : products) {
            ticketingProductsById.put(product.id, product);
            existingIds.remove(product.id);
        }

        for (String missingId : existingIds) {
            ticketingProductsById.remove(missingId);
        }
    }

    public Map<TicketingLocation.DayOfWeek, AgencyAndId> getServiceIds() {
        return serviceIds;
    }
}
