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

package org.opentripplanner.api.ws.oba_rest_api.beans;

import lombok.Data;

import java.util.List;

@Data
public class TransitMetadata {
    private long time;
    private String readableTime;
    private String validityStart;
    private String validityEnd;
    private boolean internalRequest;
    /** The bounding box of the graph, in decimal degrees. */
    private double lowerLeftLatitude, lowerLeftLongitude, upperRightLatitude, upperRightLongitude;
    /** The bounding polyline the graph, in encoded polyline string. */
    private String boundingPolyLine;
    private List<String> alertIds;
}
