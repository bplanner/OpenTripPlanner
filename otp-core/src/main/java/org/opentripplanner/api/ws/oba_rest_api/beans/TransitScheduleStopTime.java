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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import javax.xml.bind.annotation.XmlTransient;
import java.util.Collection;

@Data
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@JsonIgnoreProperties({"headsign", "stopId", "sequence"})
public class TransitScheduleStopTime extends TransitStopTime {
	@XmlTransient
	private int sequence;
	@XmlTransient
    private String headsign;
	private String tripId;
    private String serviceDate;
    private Boolean wheelchairAccessible;
    private Collection<String> groupIds;
    private Collection<String> alertIds;
}
