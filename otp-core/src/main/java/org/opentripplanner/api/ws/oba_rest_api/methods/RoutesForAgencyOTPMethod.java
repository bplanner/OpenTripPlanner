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

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "routes-for-agency" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class RoutesForAgencyOTPMethod extends RoutesForAgencyMethod {
    @Getter(AccessLevel.PROTECTED)
    @QueryParam("agencyId")
    private String agencyId;
}