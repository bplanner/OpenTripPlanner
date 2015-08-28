/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.transit_index.adapters;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement(name = "trip")
@AllArgsConstructor
public class TripsModelInfo implements Serializable {

    private static final long serialVersionUID = -4853941297409355512L;
    
    @Getter
    @XmlAttribute
    @JsonSerialize
    String headsign;

    @Getter
    @XmlAttribute
    @JsonSerialize
    Integer numberOfTrips;

    @Getter
    @XmlAttribute
    @JsonSerialize
    String calendarId;

    @Getter
    @XmlAttribute
    @JsonSerialize
    String id;

    @Getter
    @XmlAttribute
    @JsonSerialize
    String agency;

    @Getter
    boolean routeReference;

    @Getter
    boolean shapeReference;
}
