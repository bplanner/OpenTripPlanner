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

import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.common.SearchHintService;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitSearch;
import org.opentripplanner.routing.services.PatchService;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "search" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class SearchMethod extends AbstractSearchMethod<TransitEntryWithReferences<TransitSearch>> {

    @QueryParam("query") private String query;
    
    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitSearch>> getResponse() {
        PatchService patchService = graph.getService(PatchService.class);
        SearchHintService searchHintService = graph.getService(SearchHintService.class);
        
        if(query == null || query.length() == 0) {
            return responseBuilder.getResponseForSearch(query,
					Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        }

        Iterable<Stop>   allStops = transitIndexService.getAllStops().values();
        Iterable<Route> allRoutes = transitIndexService.getAllRoutes().values();

        String normalizedQuery = normalize(query);
        List<Stop>   stops     = getMatchingStopsAndStations(allStops, normalizedQuery);
        List<Route>  routes    = getMatchingRoutes(allRoutes, normalizedQuery, searchHintService);
        List<String> alertIds  = getMatchingAlerts(patchService, stops, routes);

        List<String> stopIds = null;
        if(stops != null) {
            stopIds = new ArrayList<String>(stops.size());
            for(Stop stop : stops) {
                responseBuilder.addToReferences(stop);
                stopIds.add(stop.getId().toString());
            }
        }
        
        List<String> routeIds = null;
        if(routes != null) {
            routeIds = new ArrayList<String>(routes.size());
            for(Route route : routes) {
                responseBuilder.addToReferences(route);
                routeIds.add(route.getId().toString());
            }
        }
        
        return responseBuilder.getResponseForSearch(query, stopIds, routeIds, alertIds);
    }
}
