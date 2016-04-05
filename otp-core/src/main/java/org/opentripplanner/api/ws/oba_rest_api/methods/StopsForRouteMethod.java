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
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitStopsForRoute;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.transit_index.RouteVariant;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.text.ParseException;
import java.util.List;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/stops-for-route.html">stops-for-route</a> OneBusAway API method.
 */

@Path(OneBusAwayApiMethod.API_BASE_PATH + "stops-for-route/{routeId}" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class StopsForRouteMethod extends OneBusAwayApiMethod<TransitEntryWithReferences<TransitStopsForRoute>> {

    @QueryParam("date") private String date;
    @PathParam("routeId") private String routeIdString;
    @QueryParam("includePolylines") @DefaultValue("true") private boolean includePolylines;
    
    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitStopsForRoute>> getResponse() {
        
        AgencyAndId routeId = parseAgencyAndId(routeIdString);
        Route route = transitIndexService.getAllRoutes().get(routeId);
        if(route == null) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, "Unknown routeId.", apiVersion.getApiVersion());
        }
        
        ServiceDate serviceDate = new ServiceDate();
        if(date != null) {
            try {
                serviceDate = ServiceDate.parseString(date);
            } catch (ParseException ex) {
                return TransitResponseBuilder.getFailResponse(TransitResponse.Status.INVALID_VALUE, "Failed to parse service date.", apiVersion.getApiVersion());
            }
        }
        
        long startTime = serviceDate.getAsDate(graph.getTimeZone()).getTime() / 1000;
        long endTime = serviceDate.next().getAsDate(graph.getTimeZone()).getTime() / 1000 - 1;
        
        if(!graph.transitFeedCovers(startTime) && graph.transitFeedCovers(endTime)) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NO_TRANSIT_TIMES, "Date is outside the dateset's validity.", apiVersion.getApiVersion());
        }
        
        RoutingRequest options = makeTraverseOptions(startTime, routerId);
        
        List<String> alertIds = getAlertsForRoute(routeId, options, startTime, endTime);
        List<RouteVariant> routeVariants = getReferenceVariantsForRoute(routeId);
        
        return responseBuilder.getResponseForStopsForRoute(route, routeVariants, alertIds, includePolylines);
    }
}
