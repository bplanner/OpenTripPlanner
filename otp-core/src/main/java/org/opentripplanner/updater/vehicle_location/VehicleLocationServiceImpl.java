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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import lombok.Setter;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.services.TransitIndexService;
import org.opentripplanner.util.MapUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VehicleLocationServiceImpl implements VehicleLocationService {

	private static final long VEHICLE_MAX_AGE = 300;

	private long _lastUpdateTime = Long.MIN_VALUE;

	@Setter
	private Graph graph;

	private Quadtree _quadtree = new Quadtree();

	private Map<AgencyAndId, VehicleLocation> _vehicleLocations = new HashMap<AgencyAndId, VehicleLocation>();

	private Map<AgencyAndId, VehicleLocation> _vehicleLocationsByTrip = new HashMap<AgencyAndId, VehicleLocation>();

	private Map<AgencyAndId, List<VehicleLocation>> _vehicleLocationsByRoute = new HashMap<AgencyAndId, List<VehicleLocation>>();

	private Map<String, List<VehicleLocation>> _vehicleLocationsByAgency = new HashMap<String, List<VehicleLocation>>();

	@Override
	public VehicleLocation getForVehicle(AgencyAndId vehicleId) {
		VehicleLocation vehicle = _vehicleLocations.get(vehicleId);
		return VEHICLE_FILTER.apply(vehicle) ? vehicle : null;
	}

	@Override
	public VehicleLocation getForTrip(AgencyAndId tripId) {
		VehicleLocation vehicle = _vehicleLocationsByTrip.get(tripId);
		return VEHICLE_FILTER.apply(vehicle) ? vehicle : null;
	}

	@Override
	public List<VehicleLocation> getForRoute(AgencyAndId routeId) {
		List<VehicleLocation> vehicles = _vehicleLocationsByRoute.get(routeId);
		if (vehicles == null)
			return Collections.emptyList();

		return Lists.newArrayList(Iterables.filter(vehicles, VEHICLE_FILTER));
	}

	@Override
	public List<VehicleLocation> getForAgency(String agencyId) {
		List<VehicleLocation> vehicles = _vehicleLocationsByAgency.get(agencyId);
		if (vehicles == null)
			return Collections.emptyList();

		return Lists.newArrayList(Iterables.filter(vehicles, VEHICLE_FILTER));
	}

	@Override
	public List<VehicleLocation> getForArea(Envelope area) {
		List maybeInside = _quadtree.query(area);

		List<VehicleLocation> vehicles = new LinkedList<VehicleLocation>();
		for (Object o : maybeInside) {
			VehicleLocation vehicle = (VehicleLocation) o;
			if (area.contains(vehicle.getCoordinate()))
				vehicles.add(vehicle);
		}

		return Lists.newArrayList(Iterables.filter(vehicles, VEHICLE_FILTER));
	}

	@Override
	public Collection<VehicleLocation> getAll() {
		return _vehicleLocations.values();
	}

	@Override
	public boolean add(VehicleLocation vehicle) {
		if (_vehicleLocations.containsKey(vehicle.getVehicleId())) {
			VehicleLocation existingLocation = _vehicleLocations.get(vehicle.getVehicleId());
			if (vehicle.getTimestamp() <= existingLocation.getTimestamp())
				return false;

			remove(vehicle.getVehicleId());
            
            /*if(!existingLocation.getCoordinate().equals(vehicle.getCoordinate()))
                vehicle.setBearing(new Float(DirectionUtils.getAzimuth(existingLocation.getCoordinate(), vehicle.getCoordinate())));
            else
                vehicle.setBearing(existingLocation.getBearing());*/
		}

		TransitIndexService transitIndexService = graph.getService(TransitIndexService.class);
		AgencyAndId routeId = vehicle.getRouteId();
		if (vehicle.getTripId() != null)
			routeId = transitIndexService.getTripPatternForTrip(vehicle.getTripId(), vehicle.getServiceDate()).getExemplar().getRoute().getId();

		_lastUpdateTime = System.currentTimeMillis() / 1000;

		_quadtree.insert(new Envelope(vehicle.getCoordinate()), vehicle);
		_vehicleLocations.put(vehicle.getVehicleId(), vehicle);
		MapUtils.addToMapList(_vehicleLocationsByAgency, vehicle.getVehicleId().getAgencyId(), vehicle);
		if (vehicle.getTripId() != null)
			_vehicleLocationsByTrip.put(vehicle.getTripId(), vehicle);
		if (routeId != null)
			MapUtils.addToMapList(_vehicleLocationsByRoute, routeId, vehicle);

		return true;
	}

	@Override
	public VehicleLocation remove(AgencyAndId vehicleId) {
		if (!_vehicleLocations.containsKey(vehicleId))
			return null;

		VehicleLocation vehicle = _vehicleLocations.get(vehicleId);

		TransitIndexService transitIndexService = graph.getService(TransitIndexService.class);
		AgencyAndId routeId = null;
		if (vehicle.getTripId() != null)
			routeId = transitIndexService.getTripPatternForTrip(vehicle.getTripId(), vehicle.getServiceDate()).getExemplar().getRoute().getId();

		_quadtree.remove(new Envelope(vehicle.getCoordinate()), vehicle);
		_vehicleLocations.remove(vehicleId);
		MapUtils.removeFromMapList(_vehicleLocationsByAgency, vehicle.getVehicleId().getAgencyId(), vehicle);
		if (vehicle.getTripId() != null)
			_vehicleLocationsByTrip.remove(vehicle.getTripId());
		if (routeId != null)
			MapUtils.removeFromMapList(_vehicleLocationsByRoute, routeId, vehicle);

		return vehicle;
	}

	@Override
	public void refresh(Collection<VehicleLocation> updatedLocations) {
		TransitIndexService transitIndexService = graph.getService(TransitIndexService.class);

		Quadtree quadtree = new Quadtree();
		Map<AgencyAndId, VehicleLocation> vehicleLocations = new HashMap<AgencyAndId, VehicleLocation>();
		Map<AgencyAndId, VehicleLocation> vehicleLocationsByTrip = new HashMap<AgencyAndId, VehicleLocation>();
		Map<AgencyAndId, List<VehicleLocation>> vehicleLocationsByRoute = new HashMap<AgencyAndId, List<VehicleLocation>>();
		Map<String, List<VehicleLocation>> vehicleLocationsByAgency = new HashMap<String, List<VehicleLocation>>();

		long maxTime = Long.MIN_VALUE;

		for (VehicleLocation vehicle : updatedLocations) {
			AgencyAndId routeId = vehicle.getRouteId();
			if (vehicle.getTripId() != null)
				routeId = transitIndexService.getTripPatternForTrip(vehicle.getTripId(), vehicle.getServiceDate()).getExemplar().getRoute().getId();

			quadtree.insert(new Envelope(vehicle.getCoordinate()), vehicle);
			vehicleLocations.put(vehicle.getVehicleId(), vehicle);
			MapUtils.addToMapList(vehicleLocationsByAgency, vehicle.getVehicleId().getAgencyId(), vehicle);
			if (vehicle.getTripId() != null)
				vehicleLocationsByTrip.put(vehicle.getTripId(), vehicle);
			if (routeId != null)
				MapUtils.addToMapList(vehicleLocationsByRoute, routeId, vehicle);

			if (maxTime < vehicle.getTimestamp())
				maxTime = vehicle.getTimestamp();
		}

		_lastUpdateTime = System.currentTimeMillis() / 1000;
		_quadtree = quadtree;
		_vehicleLocations = vehicleLocations;
		_vehicleLocationsByTrip = vehicleLocationsByTrip;
		_vehicleLocationsByRoute = vehicleLocationsByRoute;
		_vehicleLocationsByAgency = vehicleLocationsByAgency;
	}

	@Override
	public long getLastUpdateTime() {
		return _lastUpdateTime;
	}

	private static final VehicleFilter VEHICLE_FILTER = new VehicleFilter();
	private static class VehicleFilter implements Predicate<VehicleLocation> {

		@Override
		public boolean apply(VehicleLocation input) {
			return input != null && input.getTimestamp() + VEHICLE_MAX_AGE > System.currentTimeMillis() / 1000;
		}
	}
}

