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

import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitTicketing;
import org.opentripplanner.updater.ticketing.TicketingLocation;
import org.opentripplanner.updater.ticketing.TicketingProduct;
import org.opentripplanner.updater.ticketing.TicketingService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.Date;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "ticketing-locations" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class TicketingMethod extends OneBusAwayApiMethod<TransitEntryWithReferences<TransitTicketing>> {

    @QueryParam("ifModifiedSince")
    @DefaultValue("-1")
    long ifModifiedSince;

    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitTicketing>> getResponse() {
        TicketingService ticketingService = graph.getService(TicketingService.class);
        if (ticketingService == null) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_TICKETING_SERVICE, "Missing ticketing service.", apiVersion.getApiVersion());
        }

        List<TicketingLocation> locations;
        List<TicketingProduct> products;

        if (ifModifiedSince > 0) {
            Date date = new Date(ifModifiedSince);
            locations = ticketingService.getLocationsNewerThan(date);
            products = ticketingService.getProductsNewerThan(date);
        } else {
            locations = ticketingService.getAllLocations();
            products = ticketingService.getAllProducts();
        }

        return responseBuilder.getResponseForTicketing(locations, products);
    }
}
