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
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitAlert;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.patch.Patch;
import org.opentripplanner.routing.services.PatchService;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.util.Collection;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "alert" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class AlertMethod extends OneBusAwayApiMethod<TransitEntryWithReferences<TransitAlert>>  {

	@QueryParam("alertId") private String alertId;

	@Override
	protected TransitResponse<TransitEntryWithReferences<TransitAlert>> getResponse() {
		Alert alert = getAlert(parseAgencyAndId(alertId));
		if(alert == null)
			return TransitResponseBuilder.getFailResponse(TransitResponse.Status.NOT_FOUND, apiVersion.getApiVersion());

		return responseBuilder.getResponseForAlert(alert);
	}

	protected Alert getAlert(AgencyAndId alertId) {
		PatchService patchService = graph.getService(PatchService.class);
		if(patchService != null) {
			Collection<Patch> patches = patchService.getAllPatches();
			for(Patch patch : patches) {
				Alert alert = patch.getAlert();
				if(alert != null && alert.alertId.equals(alertId)) {
					return alert;
				}
			}
		}

		return null;
	}
}
