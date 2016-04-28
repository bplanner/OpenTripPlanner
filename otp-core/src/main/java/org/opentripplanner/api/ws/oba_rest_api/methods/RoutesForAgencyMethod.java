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

import lombok.AccessLevel;
import lombok.Getter;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.Route;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitRoute;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/routes-for-agency.html">routes-for-agency</a> OneBusAway API method.
 */

@Path(OneBusAwayApiMethod.API_BASE_PATH + "routes-for-agency/{agencyId}" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class RoutesForAgencyMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitRoute>> {

    @Getter(AccessLevel.PROTECTED)
    @PathParam("agencyId")
    private String agencyId;

    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitRoute>> getResponse() {
        if (getAgencyId() != null && transitIndexService.getAgency(getAgencyId()) == null) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, apiVersion.getApiVersion());
        }

        List<Route> routesForAgency = new LinkedList<Route>();

        for (Route route : transitIndexService.getAllRoutes().values()) {
            if (getAgencyId() == null || route.getId().getAgencyId().equals(getAgencyId())) {
                routesForAgency.add(route);
            }
        }

        return responseBuilder.getResponseForRoutes(routesForAgency);
    }
}
