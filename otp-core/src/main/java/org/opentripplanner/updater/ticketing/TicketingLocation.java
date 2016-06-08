package org.opentripplanner.updater.ticketing;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Builder;
import org.onebusaway.gtfs.serialization.mappings.InvalidStopTimeException;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@ToString
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id", "lon", "lat"})
public class TicketingLocation {

    public String id;
    public PlaceType type;
    public TicketingState state;
    public boolean visible;
    public String place;
    public String address;
    public String description;
    public String operator;
    public double lat;
    public double lon;
    public boolean cashAccepted;
    public boolean creditCardsAccepted;
    public boolean passIdCreation;
    public boolean ticketPassExchange;
    public List<TicketingPeriod> openingPeriods;
    public List<String> products;

    public Date lastModified;

    @Getter
    @Builder
    public static class TicketingPeriod {
        private DayOfWeek dayOfWeek;
        private String opens;
        private String closes;

        private int opensSeconds;
        private int closesSeconds;

        public TicketingPeriod(DayOfWeek dayOfWeek, String opens, String closes, int opensSeconds, int closesSeconds) {
            this.dayOfWeek = dayOfWeek;
            this.opens = opens;
            this.closes = closes;

            if(opens != null && opens.lastIndexOf(":") + 2 == opens.length())
                opens += "0";
            if(closes != null && closes.lastIndexOf(":") + 2 == closes.length())
                closes += "0";

            try {
                if (opens != null)
                    this.opensSeconds = StopTimeFieldMappingFactory.getStringAsSeconds(opens + ":00");
                if (closes != null)
                    this.closesSeconds = StopTimeFieldMappingFactory.getStringAsSeconds(closes + ":00");
            } catch (InvalidStopTimeException e) {
                throw e;
            }
        }
    }

    @AllArgsConstructor
    public enum DayOfWeek implements IntEnum {
        MON(1), TUE(2), WED(3), THU(4), FRI(5), SAT(6), SUN(0), HOL(7), O247(8);

        @Getter
        private int type;
    }

    @AllArgsConstructor
    public enum TicketingState implements IntEnum {
        PLANNED(0), OPERATIONAL(1), INOPERATIVE(2);

        @Getter
        private int type;
    }

    @AllArgsConstructor
    public enum PlaceType implements IntEnum {
        CUSTOMER_CENTER(1), CASHIER(2), VENDING_MACHINE(3), RESELLER(4);

        @Getter
        private int type;
    }

    private interface IntEnum {
        int getType();
    }

    static <T extends Enum & IntEnum> T mapIntToEnum(Class<T> clazz, int type) {
        for (T v : clazz.getEnumConstants()) {
            if (v.getType() == type) {
                return v;
            }
        }

        return null;
    }

    static <T extends Enum & IntEnum> List<T> mapIntListToEnumList(Class<T> clazz, List<Integer> type) {
        List<T> values = new ArrayList<T>(type.size());
        for (int t : type) {
            T value = mapIntToEnum(clazz, t);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }
}
