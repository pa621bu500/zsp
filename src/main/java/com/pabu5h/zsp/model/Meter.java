package com.pabu5h.zsp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Meter {

    // ************* available variable from here on *************

    Integer  recordID;			// to store recid
    Integer  tenantId;			// to store tenant_id
    Integer  billType;			// to store the billing type
    Integer  groupID;			// to store grouping ID for billing type 0
    String meterID;			// to store the meter id
    String meterID2;		// to store the 2nd meter id

    double  deposit;			// deposit amount
    String tenant;			// tenant_name (display on the left)
    String addr1;			// address line 1 (display on the left)
    String addr2;			// address line 2 (display on the left)
    String leftUnit;		// unit number (display on the left)
    String leftPostal;		// postal code (display on the left)

    String unitName;		// unit_name (display on the right)
    String street;			// street (display on the right)
    String floor;			// floot (display on the right)
    String rightUnit;		// unit number (display on the right)
    String rightPostal;		// postal code (display on the right)

    Integer  powerfactor;		// power factor of the meter
    Integer  giro;				// tenant using giro or not (1 for using, 0 for not using)

    double startRead;		// meter's kwhtot start read
    double endRead;			// meter's kwhtot end read
    double usage;			// meter's usage of the month
    double usageCost;		// usage's cost
    double usageCostGST;	// GST of usage's cost

    double m2startReading;
    double m2endReading;

    String periodOfLatePayment;	// late payment start date
    double lateCharges; 	// late payment charges

    double balanceFromPrevMonth;	// balance from previous month
    double amtReceived;		// payment amount received for previous month



// Constructor for the available fields in the first query
    public Meter(Integer  recordID, Integer  tenantId, Integer  billType, Integer  groupID, String meterID, double  deposit, String tenant,
                 String addr1, String addr2, String leftUnit, String leftPostal, String unitName, String street,
                 String floor, String rightUnit, String rightPostal, Integer  powerfactor, Integer  giro) {
        this.recordID = recordID;
        this.tenantId = tenantId;
        this.billType = billType;
        this.groupID = groupID;
        this.meterID = meterID;
        this.deposit = deposit;
        this.tenant = tenant;
        this.addr1 = addr1;
        this.addr2 = addr2;
        this.leftUnit = leftUnit;
        this.leftPostal = leftPostal;
        this.unitName = unitName;
        this.street = street;
        this.floor = floor;
        this.rightUnit = rightUnit;
        this.rightPostal = rightPostal;
        this.powerfactor = powerfactor;
        this.giro = giro;
    }

}