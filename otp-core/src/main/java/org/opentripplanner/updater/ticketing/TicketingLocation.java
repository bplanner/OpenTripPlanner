package org.opentripplanner.updater.ticketing;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Builder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@ToString
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lastModified"})
public class TicketingLocation {

    private String id;
    private PlaceType type;
    private TicketingState state;
    private boolean visible;
    private String place;
    private String address;
    private String description;
    private String operator;
    private double lat;
    private double lng;
    private boolean cachAccepted;
    private boolean creditCardsAccepted;
    private boolean passIdCreation;
    private boolean ticketPassExchange;
    private List<TicketingPeriod> openingPeriods;
    private List<TicketProduct> products;

    private Date lastModified;

    @Getter
    @Builder
    public static class TicketingPeriod {
        private DayOfWeek dayOfWeek;
        private String opens;
        private String closes;
    }

    @AllArgsConstructor
    public enum DayOfWeek implements IntEnum {
        MON(1), TUE(2), WED(3), THU(4), FRI(5), SAT(6), SUN(0);

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

    @AllArgsConstructor
    public enum TicketProduct implements IntEnum {
        ALFA(0);

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
