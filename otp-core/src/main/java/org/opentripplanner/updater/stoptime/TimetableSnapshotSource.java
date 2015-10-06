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

package org.opentripplanner.updater.stoptime;

import lombok.Setter;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.opentripplanner.routing.core.ServiceIdToNumberService;
import org.opentripplanner.routing.edgetype.TableTripPattern;
import org.opentripplanner.routing.edgetype.TimetableResolver;
import org.opentripplanner.routing.edgetype.factory.GTFSPatternHopFactory;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.raptor.RaptorDataService;
import org.opentripplanner.routing.services.PatchService;
import org.opentripplanner.routing.services.TransitIndexService;
import org.opentripplanner.routing.trippattern.TripUpdateList;
import org.opentripplanner.routing.trippattern.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * This class should be used to create snapshots of lookup tables of realtime data. This is
 * necessary to provide planning threads a consistent constant view of a graph with realtime data at
 * a specific point in time.
 */
public class TimetableSnapshotSource {

    private static final Logger LOG = LoggerFactory.getLogger(TimetableSnapshotSource.class);

    @Setter    private int logFrequency = 2000;
    
    private int appliedBlockCount = 0;

    /** 
     * If a timetable snapshot is requested less than this number of milliseconds after the previous 
     * snapshot, just return the same one. Throttles the potentially resource-consuming task of 
     * duplicating a TripPattern -> Timetable map and indexing the new Timetables.
     */
    @Setter private int maxSnapshotFrequency = 1000; // msec    

    /** 
     * The last committed snapshot that was handed off to a routing thread. This snapshot may be
     * given to more than one routing thread if the maximum snapshot frequency is exceeded. 
     */
    private TimetableResolver snapshot = null;
    
    /** The working copy of the timetable resolver. Should not be visible to routing threads. */
    private TimetableResolver buffer = new TimetableResolver();
    
    /** Should expired realtime data be purged from the graph. */
    @Setter private boolean purgeExpiredData = true;
    
    /** The TransitIndexService */
    private TransitIndexService transitIndexService;
    
    private Graph graph;
    
    /**
     * Factory used for adding new patterns for trips to the graph.
     */
    private GTFSPatternHopFactory hopFactory = new GTFSPatternHopFactory();
    
    protected ServiceDate lastPurgeDate = null;
    
    protected long lastSnapshotTime = -1;
    
    public TimetableSnapshotSource(Graph graph) {
        this.graph = graph;
        transitIndexService = graph.getService(TransitIndexService.class);
        if (transitIndexService == null)
            throw new RuntimeException(
                    "Real-time update need a TransitIndexService. Please setup one during graph building.");

	    snapshot = buffer.commit(true);
    }
    
    /**
     * @return an up-to-date snapshot mapping TripPatterns to Timetables. This snapshot and the
     *         timetable objects it references are guaranteed to never change, so the requesting
     *         thread is provided a consistent view of all TripTimes. The routing thread need only
     *         release its reference to the snapshot to release resources.
     */
    public TimetableResolver getTimetableSnapshot() {
	    return snapshot;
/*	    long time = System.currentTimeMillis();
	    try {
			return getTimetableSnapshot(false);
	    } finally {
		    long diff = System.currentTimeMillis() - time;
		    if(diff > 5) {
			    LOG.warn("Waited " + diff + "ms for snapshot.");
		    }
	    }*/
    }
    
    protected synchronized TimetableResolver getTimetableSnapshot(boolean force) {
        long now = System.currentTimeMillis();
        if (force || now - lastSnapshotTime > maxSnapshotFrequency) {
            if (force || buffer.isDirty()) {
                LOG.debug("Committing {}", buffer.toString());
                snapshot = buffer.commit(force);
	            long diff = System.currentTimeMillis() - now;
	            LOG.warn("Committed changes in " + diff + "ms");
            } else {
                LOG.debug("Buffer was unchanged, keeping old snapshot.");
            }
            lastSnapshotTime = System.currentTimeMillis();
        } else {
            LOG.debug("Snapshot frequency exceeded. Reusing snapshot {}", snapshot);
        }
        return snapshot;
    }
    

    /**
     * Method to apply a trip update list to the most recent version of the timetable snapshot.
     */
    public void applyTripUpdateLists(Collection<TripUpdateList> updates) {
        if (updates == null) {
            LOG.debug("updates is null");
            return;
        }

        LOG.debug("message contains {} trip update blocks", updates.size());
        int uIndex = 0;
        boolean hasNewAddedTrips = false;
        for (TripUpdateList tripUpdateList : updates) {
            uIndex += 1;
            LOG.debug("trip update block #{} ({} updates) :", uIndex, tripUpdateList.getUpdates().size());
            LOG.trace("{}", tripUpdateList);
            
            try {
                boolean applied = false;
                boolean newAddedTrip = TripUpdateList.Status.ADDED == tripUpdateList.getStatus() && isNewAddedTrip(tripUpdateList);
                switch(tripUpdateList.getStatus()) {
                case ADDED:
                    applied = handleAddedTrip(tripUpdateList);
                    break;
                case CANCELED:
                    applied = handleCanceledTrip(tripUpdateList);
                    break;
                case UPDATED:
                    applied = handleUpdatedTrip(tripUpdateList);
                    break;
                case MODIFIED:
                    applied = handleModifiedTrip(tripUpdateList);
                    break;
                case REMOVED:
                    applied = handleRemovedTrip(tripUpdateList);
                    break;
                }

                if(applied) {
                    hasNewAddedTrips = hasNewAddedTrips || newAddedTrip;
                    appliedBlockCount++;
                 }
            }
            catch(Exception e) {
                LOG.warn("Failed to apply TripUpdateList: {}\n{}", e, tripUpdateList);
            }

            if (appliedBlockCount % logFrequency == 0) {
                LOG.info("Applied {} stoptime update blocks.", appliedBlockCount);
            }
        }
        LOG.debug("end of update message");

        PatchService patchService = graph.getService(PatchService.class);
        if(patchService != null && hasNewAddedTrips)
            patchService.reapplyPatches();
        
        // Make a snapshot after each message in anticipation of incoming requests
        // Purge data if necessary (and force new snapshot if anything was purged)
        if(purgeExpiredData) {
            boolean modified = purgeExpiredData(); 
            getTimetableSnapshot(modified);
        }
        else {
            getTimetableSnapshot(false);
        }

    }

    protected boolean isNewAddedTrip(TripUpdateList tripUpdateList) {
        return transitIndexService.getTripPatternForTrip(tripUpdateList.getTripId(), tripUpdateList.getServiceDate()) == null;
    }

    protected boolean handleAddedTrip(TripUpdateList tripUpdateList) {
        if(isNewAddedTrip(tripUpdateList)) {
            addAddedTrip(tripUpdateList);
        }

        return handleUpdatedTrip(tripUpdateList);
    }

    protected void addAddedTrip(TripUpdateList tripUpdateList) {
        ServiceDate serviceDate = tripUpdateList.getServiceDate();
        Trip trip = tripUpdateList.getTrip();

        ServiceIdToNumberService serviceIdToNumberService = graph.getService(ServiceIdToNumberService.class);
        CalendarService calendarService = graph.getCalendarService();

        AgencyAndId serviceId = trip.getServiceId();
        if(serviceId == null) {
            serviceId = new AgencyAndId(trip.getId().getAgencyId(), "ADDED-SERVICE-" + serviceDate);
            trip.setServiceId(serviceId);
        }
        if(!calendarService.getServiceIds().contains(serviceId)) {
            addService(serviceId, Collections.singletonList(serviceDate));
        }

        AgencyAndId routeId = trip.getRoute().getId();
        Route route = transitIndexService.getAllRoutes().get(routeId);
        if(route == null) {
            throw new RuntimeException("Missing route for trip: " + routeId + "\n" + tripUpdateList);
        }
        trip.setRoute(route);
        
        List<StopTime> stopTimes = new LinkedList<StopTime>();
        for(Update update : tripUpdateList.getUpdates()) {
            Stop stop = transitIndexService.getAllStops().get(update.stopId);

            StopTime stopTime = new StopTime();
            stopTime.setTrip(trip);
            stopTime.setStop(stop);
            stopTime.setStopSequence(update.stopSeq == null ? stopTimes.size() : update.stopSeq);
            stopTime.setArrivalTime(update.arrive);
            stopTime.setDepartureTime(update.depart);

            stopTimes.add(stopTime);
            trip.setTripHeadsign(stop.getName());
        }

        hopFactory.bootstrapContextFromTransitIndex(transitIndexService, calendarService, serviceIdToNumberService);
        GTFSPatternHopFactory.Result result = hopFactory.addPatternForTripToGraph(graph, trip, stopTimes);
        TableTripPattern tripPattern = result.tripPattern;

        hopFactory.augmentServiceIdToNumberService(serviceIdToNumberService);
        transitIndexService.add(result, serviceDate);

        LOG.info("Added trip: {}/{} @ {} to {}", trip.getRoute().getId(), trip.getId(), serviceDate.toString(), tripPattern);

        if(graph.getService(RaptorDataService.class) != null) {
            RaptorDataService raptorDataService = graph.getService(RaptorDataService.class);
            if(!raptorDataService.addTripPattern(graph, tripPattern)) {
                LOG.warn("Failed to add ADDED trip pattern to raptor index: {} @ {} to {}", trip.getId(), serviceDate.toString(), tripPattern);
            }
        }
    }

    protected boolean handleCanceledTrip(TripUpdateList tripUpdateList) {

        TableTripPattern pattern = getPatternForTrip(tripUpdateList.getTripId(), tripUpdateList.getServiceDate());
        if (pattern == null) {
            LOG.debug("No pattern found for tripId {}, skipping UpdateBlock.", tripUpdateList.getTripId());
            return false;
        }

        return buffer.update(pattern, tripUpdateList);
    }

    protected boolean handleUpdatedTrip(TripUpdateList tripUpdateList) {

        tripUpdateList.filter(true, true, true);
        if (! tripUpdateList.isCoherent()) {
            throw new RuntimeException("Incoherent TripUpdate, skipping.");
        }
        if (tripUpdateList.getUpdates().size() < 1) {
            throw new RuntimeException("TripUpdate contains no updates after filtering, skipping.");
        }
        TableTripPattern pattern = getPatternForTrip(tripUpdateList.getTripId(), tripUpdateList.getServiceDate());
        if (pattern == null) {
            LOG.info("Received modified update for non-existing trip (no pattern exists): {}", tripUpdateList.getTripId());
            return false;
        }

        // we have a message we actually want to apply
        return buffer.update(pattern, tripUpdateList);
    }

    protected boolean handleModifiedTrip(TripUpdateList tripUpdateList) {
        Trip newTrip, existingTrip, updatedTrip;
        TableTripPattern existingPattern;
        
        newTrip = tripUpdateList.getTrip();
        existingPattern = transitIndexService.getTripPatternForTrip(newTrip.getId(), tripUpdateList.getServiceDate());
        if(existingPattern == null) {
            LOG.info("Received modified update for non-existing trip (no pattern exists): ", tripUpdateList.getTripId());
            return false;
        }
        
        newTrip = tripUpdateList.getTrip();
        
        existingTrip = existingPattern.getTrip(newTrip.getId());
        
        updatedTrip = new Trip(existingTrip);
        updatedTrip.setServiceId(null);
        
        if(tripUpdateList.getWheelchairAccessible() != null)
            updatedTrip.setWheelchairAccessible(tripUpdateList.getWheelchairAccessible());
        
        TripUpdateList cancelingTripUpdateList = TripUpdateList.forCanceledTrip(
                tripUpdateList.getTripId(), tripUpdateList.getTimestamp(), tripUpdateList.getServiceDate());
        TripUpdateList addingTripUpdateList = TripUpdateList.forAddedTrip(
                updatedTrip, tripUpdateList.getTimestamp(), tripUpdateList.getServiceDate(), tripUpdateList.getUpdates());

        return handleCanceledTrip(cancelingTripUpdateList) && handleAddedTrip(addingTripUpdateList);
    }

    protected boolean handleRemovedTrip(TripUpdateList tripUpdateList) {
        // TODO: Handle removed trip
        
        return false;
    }

    protected void addService(AgencyAndId serviceId, List<ServiceDate> days) {
        CalendarServiceData data = graph.getService(CalendarServiceData.class);
        data.putServiceDatesForServiceId(serviceId, days);

        LocalizedServiceId localizedServiceId = new LocalizedServiceId(serviceId, graph.getTimeZone());
        List<Date> dates = new LinkedList<Date>();
        for(ServiceDate day : days) {
            dates.add(day.getAsDate(graph.getTimeZone()));
        }
        data.putDatesForLocalizedServiceId(localizedServiceId, dates);
    }

    protected boolean purgeExpiredData() {
        ServiceDate today = new ServiceDate();
        ServiceDate previously = today.previous().previous(); // Just to be safe... 
        
        if(lastPurgeDate != null && lastPurgeDate.compareTo(previously) > 0) {
            return false;
        }
        
        LOG.debug("purging expired realtime data");
        // TODO: purge expired realtime data
        
        lastPurgeDate = previously;
        
        return buffer.purgeExpiredData(previously);
    }

    protected TableTripPattern getPatternForTrip(AgencyAndId tripId, ServiceDate serviceDate) {
        TableTripPattern pattern = transitIndexService.getTripPatternForTrip(tripId, serviceDate);
        return pattern;
    }

}
