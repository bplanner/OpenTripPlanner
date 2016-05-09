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

import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.updater.ticketing.TicketingLocation;
import org.opentripplanner.updater.ticketing.TicketingService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.Date;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "ticketing-locations" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class TicketingMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TicketingLocation>> {

    @QueryParam("ifModifiedSince")
    @DefaultValue("-1")
    long ifModifiedSince;

    @Override
    protected TransitResponse<TransitListEntryWithReferences<TicketingLocation>> getResponse() {
        TicketingService ticketingService = graph.getService(TicketingService.class);
        if (ticketingService == null) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_TICKETING_SERVICE, "Missing ticketing service.", apiVersion.getApiVersion());
        }

        if (ifModifiedSince > 0) {
            return responseBuilder.getResponseForList(ticketingService.getLocationsNewerThan(new Date(ifModifiedSince)));
        } else {
            return responseBuilder.getResponseForList(ticketingService.getAllLocations());
        }
    }
}
