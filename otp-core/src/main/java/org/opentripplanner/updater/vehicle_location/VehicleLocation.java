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

package org.opentripplanner.updater.vehicle_location;

import com.vividsolutions.jts.geom.Coordinate;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

/**
 * Describes the position of a vehicle on a trip;
 */
@ToString
public class VehicleLocation {

    public VehicleLocation(AgencyAndId routeId, Float lat, Float lon,
                           AgencyAndId tripId, Float bearing, Status status, AgencyAndId stopId, ServiceDate serviceDate) {
        this(0l, null, routeId, lat, lon, tripId, null, null, bearing, status,
                stopId, null, serviceDate, null, false, null, null, null, null, null, null);
    }

    public VehicleLocation(long timestamp, AgencyAndId vehicleId, AgencyAndId routeId, Float lat, Float lon,
                           AgencyAndId tripId, String licensePlate, String label, Float bearing, Status status,
                           AgencyAndId stopId, Integer stopSequence, ServiceDate serviceDate, CongestionLevel congestionLevel, boolean deviated,
                           String busPhoneNumber, String driverName, Integer vehicleRouteType, String blockId, Integer stopDistancePercent, String vehicleModel) {
        this.timestamp = timestamp;
        this.vehicleId = vehicleId;
        this.latitude = lat;
        this.longitude = lon;
        this.routeId = routeId;
        this.tripId = tripId;
        this.bearing = bearing;
        this.status = status;
        this.licensePlate = licensePlate;
        this.label = label;
        this.stopId = stopId;
        this.stopSequence = stopSequence;
        this.serviceDate = serviceDate;
        this.congestionLevel = congestionLevel;
        this.deviated = deviated;
        this.busPhoneNumber = busPhoneNumber;
        this.driverName = driverName;
        this.vehicleRouteType = vehicleRouteType;
        this.blockId = blockId;
        this.stopDistancePercent = stopDistancePercent;
        this.vehicleModel = vehicleModel;
    }

    public Coordinate getCoordinate() {
        if (coordinate == null)
            coordinate = new Coordinate(longitude, latitude);
        return coordinate;
    }

    @Getter
    private long timestamp;

    @Getter
    private AgencyAndId vehicleId;

    @Getter
    private Float latitude;

    @Getter
    private Float longitude;

    @Getter
    private Float bearing;

    @Getter
    @Setter
    private AgencyAndId routeId;

    @Getter
    @Setter
    private AgencyAndId tripId;

    @Getter
    private String licensePlate;

    @Getter
    @Setter
    private String label;

    @Getter
    private Status status;

    @Getter
    @Setter
    private AgencyAndId stopId;

    @Getter
    private Integer stopSequence;

    @Getter
    private ServiceDate serviceDate;

    @Getter
    private CongestionLevel congestionLevel;

    @Getter
    private String busPhoneNumber;

    @Getter
    private String driverName;

    @Getter
    private boolean deviated;

    @Getter
    private Integer vehicleRouteType;

    @Getter
    private String blockId;

    @Getter
    private Integer stopDistancePercent;

    @Getter
    private String vehicleModel;

    private Coordinate coordinate;

    public enum Status {
        INCOMING_AT,
        STOPPED_AT,
        IN_TRANSIT_TO
    }

    public enum CongestionLevel {
        UNKNOWN,
        CONGESTION
    }
}
