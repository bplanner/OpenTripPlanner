
package org.opentripplanner.api.ws.oba_rest_api.beans;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.updater.vehicle_location.VehicleLocation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class TransitVehicle {
    private String vehicleId;
    private String stopId;
    private String routeId;
    private Float bearing;
    private TransitCoordinatePoint location;
    private String serviceDate;
    private String licensePlate;
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    private String label;
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    private String model;
    private boolean deviated;
    private long lastUpdateTime;
    private VehicleLocation.Status status;
    private VehicleLocation.CongestionLevel congestionLevel;
	private TraverseMode vehicleRouteType;
	private Integer stopDistancePercent;

	@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
	private String busPhoneNumber;

	@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
	private String driverName;

	@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
	private String blockId;

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    private String tripId;
}
