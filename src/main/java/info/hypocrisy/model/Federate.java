package info.hypocrisy.model;

import com.sun.org.apache.bcel.internal.generic.VariableLengthInstruction;
import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAunicodeString;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.exceptions.*;
import hla.rti1516e.time.HLAfloat64Interval;
import hla.rti1516e.time.HLAfloat64Time;
import se.pitch.prti1516e.time.HLAfloat64IntervalImpl;
import se.pitch.prti1516e.time.HLAfloat64TimeImpl;

import java.util.*;
import java.net.URL;
/**
 * Created by Hypocrisy on 3/23/2016.
 * This class implements a NullFederateAmbassador.
 */
public class Federate extends NullFederateAmbassador implements Runnable{
    private boolean state = true;
    private boolean status = false;
    private RTIambassador _rtiAmbassador;
    private InteractionClassHandle _messageId;
    private ParameterHandle _parameterIdText;
    private ParameterHandle _parameterIdSender;
    private ObjectInstanceHandle _userId;
    private AttributeHandle _attributeIdName;
    private String _username;

    private volatile boolean _reservationComplete;
    private volatile boolean _reservationSucceeded;
    private final Object _reservationSemaphore = new Object();

    private EncoderFactory _encoderFactory;

    private final Map<ObjectInstanceHandle, Participant> _knownObjects = new HashMap<ObjectInstanceHandle, Participant>();

    private static class Participant {
        private final String name;

        Participant(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    public Federate() {
        // For test
        federateAttributes = new FederateAttributes();
        federateAttributes.setName("TestFederate");
        federateAttributes.setFederation("TestFederation");
        federateAttributes.setCrcAddress("localhost");
        federateAttributes.setFomName("HLADemo");
        federateAttributes.setFomUrl("http://localhost:8080/assets/config/HLADemo.xml");
        federateAttributes.setStrategy("Regulating and Constrained");
        //federateAttributes.setTime(this.getTimeToMoveTo());
        //federateAttributes.setStatus(status);
        federateAttributes.setStep("1");
        federateAttributes.setLookahead("1");
    }

    private FederateAttributes federateAttributes;
    public Federate(FederateParameters federateParameters) {
        federateAttributes = new FederateAttributes();
        federateAttributes.setName(federateParameters.getFederateName());
        federateAttributes.setFederation(federateParameters.getFederationName());
        federateAttributes.setCrcAddress(federateParameters.getCrcAddress());
        String[] tmp = federateParameters.getFomUrl().split("/");
        federateAttributes.setFomName(tmp[tmp.length - 1]);
        federateAttributes.setFomUrl(federateParameters.getFomUrl());
        federateAttributes.setStrategy(federateParameters.getStrategy());
        //federateAttributes.setTime(this.getTimeToMoveTo());
        //federateAttributes.setStatus(status);
        federateAttributes.setStep(federateParameters.getStep());
        federateAttributes.setLookahead(federateParameters.getLookahead());
    }

    public boolean getState() {
        return state;
    }
    public void setState(boolean state) {
        this.state = state;
    }
    public boolean getStatus() {
        return status;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }

    public FederateAttributes getFederateAttributes() {
        federateAttributes.setTime(timeToMoveTo.getValue());
        federateAttributes.setStatus(status);
        return federateAttributes;
    }

    private HLAfloat64Time timeToMoveTo;
    private HLAfloat64Interval advancedStep;
    public Double getTimeToMoveTo() {
        return timeToMoveTo.getValue();
    }

    public void createAndJoin() {
        try {
            /**********************
             * get Rti ambassador and connect with it.
             **********************/
            try {
                RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
                _rtiAmbassador = rtiFactory.getRtiAmbassador();
                _encoderFactory = rtiFactory.getEncoderFactory();
            } catch (Exception e) {
                System.out.println("Unable to create RTI ambassador.");
                return;
            }

            String crcAddress = federateAttributes.getCrcAddress();
            String settingsDesignator = "crcAddress=" + crcAddress;
            _rtiAmbassador.connect(this, CallbackModel.HLA_IMMEDIATE, settingsDesignator);

            /**********************
             * Clean up old federation
             **********************/
            try {
                _rtiAmbassador.destroyFederationExecution(federateAttributes.getFederation());
            } catch (FederatesCurrentlyJoined ignored) {
            } catch (FederationExecutionDoesNotExist ignored) {
            }

            /**********************
             * Create federation
             **********************/
            //String s = "http://localhost:8080/assets/config/HLADemo.xml";
            URL url = new URL(federateAttributes.getFomUrl());
            try {
                _rtiAmbassador.createFederationExecution(federateAttributes.getFederation(), new URL[]{url}, "HLAfloat64Time");
            } catch (FederationExecutionAlreadyExists ignored) {
            }

            /**********************
             * Join current federate(specified with this) into current federation(specified with _rtiAmbassador)
             **********************/
            _rtiAmbassador.joinFederationExecution(federateAttributes.getName(), federateAttributes.getFederation(), new URL[]{url});

            /**********************
             * Add by Hypocrisy on 03/28/2015
             * Time Management Variables.
             **********************/
            HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getLookahead()) );
            //_rtiAmbassador->enableAsynchronousDelivery();
            if ("Regulating".equals(federateAttributes.getStrategy())) {
                _rtiAmbassador.enableTimeRegulation(lookahead);
            } else if ("Constrained".equals(federateAttributes.getStrategy())) {
                _rtiAmbassador.enableTimeConstrained();
            } else if("Regulating and Constrained".equals(federateAttributes.getStrategy())){
                _rtiAmbassador.enableTimeRegulation(lookahead);
                _rtiAmbassador.enableTimeConstrained();
            } else {
                //_rtiAmbassador.disableTimeRegulation();   // If it is not enabled, this method will throw exception.
                //_rtiAmbassador.disableTimeConstrained();
            }
            timeToMoveTo = new HLAfloat64TimeImpl(0);
            advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(federateAttributes.getStep()) );
            _rtiAmbassador.enableCallbacks();
        } catch (Exception e) {
            System.out.println("Unable to join");
        }
    }

    public void update(UpdateParameters updateParameters) {
        federateAttributes.setStrategy(updateParameters.getStrategy());
        federateAttributes.setStep(updateParameters.getStep());
        federateAttributes.setLookahead(updateParameters.getLookahead());

        HLAfloat64Interval lookahead = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getLookahead()) );
        //_rtiAmbassador->enableAsynchronousDelivery();
        try {
            if ("Regulating".equals(federateAttributes.getStrategy())) {
                try {
                    _rtiAmbassador.enableTimeRegulation(lookahead);
                } catch (Exception e) {
                    _rtiAmbassador.disableTimeRegulation();
                    _rtiAmbassador.enableTimeRegulation(lookahead);
                    System.out.println("Time regulating already enabled");
                }
                try {
                    _rtiAmbassador.disableTimeConstrained();
                } catch (Exception e) {
                    System.out.println("Time constrained has't been enabled");
                }
            } else if ("Constrained".equals(federateAttributes.getStrategy())) {
                try {
                    _rtiAmbassador.enableTimeConstrained();
                } catch (Exception e) {
                    System.out.println("Time constrained already enabled");
                }
                try {
                    _rtiAmbassador.disableTimeRegulation();
                } catch (Exception e) {
                    System.out.println("Time regulating has't been enabled");
                }
            } else if ("Regulating and Constrained".equals(federateAttributes.getStrategy())) {
                try {
                    _rtiAmbassador.enableTimeRegulation(lookahead);
                } catch (Exception e) {
                    _rtiAmbassador.disableTimeRegulation();
                    _rtiAmbassador.enableTimeRegulation(lookahead);
                    System.out.println("Time regulating already enabled");
                }
                try {
                    _rtiAmbassador.enableTimeConstrained();
                } catch (Exception e) {
                    System.out.println("Time Constrained already enabled");
                }
            } else {
                try {
                    _rtiAmbassador.disableTimeRegulation();
                } catch (Exception e) {
                    System.out.println("Time regulating has't been enabled");
                }
                try {
                    _rtiAmbassador.disableTimeConstrained();
                } catch (Exception e) {
                    System.out.println("Time constrained has't been enabled");
                }
            }
        } catch (Exception e) {

        }
        advancedStep = new HLAfloat64IntervalImpl( Double.parseDouble(updateParameters.getStep()) );
    }

    public boolean isFirst = true;
    @Override
    public void run() {
        try {
            /**********************
             * Subscribe and publish interactions
             **********************/
            _messageId = _rtiAmbassador.getInteractionClassHandle("Communication");
            _parameterIdText = _rtiAmbassador.getParameterHandle(_messageId, "Message");
            _parameterIdSender = _rtiAmbassador.getParameterHandle(_messageId, "Sender");

            _rtiAmbassador.subscribeInteractionClass(_messageId);
            _rtiAmbassador.publishInteractionClass(_messageId);

            /**********************
             * Subscribe and publish objects
             **********************/
            ObjectClassHandle participantId = _rtiAmbassador.getObjectClassHandle("Participant");
            _attributeIdName = _rtiAmbassador.getAttributeHandle(participantId, "Name");

            AttributeHandleSet attributeSet = _rtiAmbassador.getAttributeHandleSetFactory().create();
            attributeSet.add(_attributeIdName);

            _rtiAmbassador.subscribeObjectClassAttributes(participantId, attributeSet);
            _rtiAmbassador.publishObjectClassAttributes(participantId, attributeSet);

            /**********************
             * Reserve object instance name and register object instance
             **********************/
            do {
                Calendar cal = Calendar.getInstance();
                _username = "hecate" + cal.get(Calendar.SECOND);

                try {
                    _reservationComplete = false;
                    _rtiAmbassador.reserveObjectInstanceName(_username);
                    synchronized (_reservationSemaphore) {
                        // Wait for response from RTI
                        while (!_reservationComplete) {
                            try {
                                _reservationSemaphore.wait();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    if (!_reservationSucceeded) {
                        System.out.println("Name already taken, try again.");
                        return;
                    }
                } catch (IllegalName e) {
                    //System.out.println("Illegal name. Try again.");
                } catch (RTIexception e) {
                    //System.out.println("RTI exception when reserving name: " + e.getMessage());
                    return;
                }
            } while (!_reservationSucceeded);

            _userId = _rtiAmbassador.registerObjectInstance(participantId, _username);

            while(state) {
                Thread.sleep(1000);
                if(status) {
                    try {
                        timeToMoveTo = timeToMoveTo.add(advancedStep);
                        _rtiAmbassador.timeAdvanceRequest(timeToMoveTo);
                    } catch (Exception e) {
                        timeToMoveTo = timeToMoveTo.subtract(advancedStep);
                    }

                    HLAunicodeString nameEncoder = _encoderFactory.createHLAunicodeString(_username);

                    String message = "Hello";

                    ParameterHandleValueMap parameters = _rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = _encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(_parameterIdText, messageEncoder.toByteArray());
                    parameters.put(_parameterIdSender, nameEncoder.toByteArray());
                    _rtiAmbassador.sendInteraction(_messageId, parameters, null);
                    //_rtiAmbassador.sendInteraction(_messageId, parameters, null, timeToMoveTo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void test() {
        try {
            /**********************
             * Subscribe and publish interactions
             **********************/
            _messageId = _rtiAmbassador.getInteractionClassHandle("Communication");
            _parameterIdText = _rtiAmbassador.getParameterHandle(_messageId, "Message");
            _parameterIdSender = _rtiAmbassador.getParameterHandle(_messageId, "Sender");

            _rtiAmbassador.subscribeInteractionClass(_messageId);
            _rtiAmbassador.publishInteractionClass(_messageId);

            /**********************
             * Subscribe and publish objects
             **********************/
            ObjectClassHandle participantId = _rtiAmbassador.getObjectClassHandle("Participant");
            _attributeIdName = _rtiAmbassador.getAttributeHandle(participantId, "Name");

            AttributeHandleSet attributeSet = _rtiAmbassador.getAttributeHandleSetFactory().create();
            attributeSet.add(_attributeIdName);

            _rtiAmbassador.subscribeObjectClassAttributes(participantId, attributeSet);
            _rtiAmbassador.publishObjectClassAttributes(participantId, attributeSet);

            /**********************
             * Reserve object instance name and register object instance
             **********************/
            /*
            do {
                Calendar cal = Calendar.getInstance();
                _username = "hecate" + cal.get(Calendar.SECOND);

                try {
                    _reservationComplete = false;
                    _rtiAmbassador.reserveObjectInstanceName(_username);
                    synchronized (_reservationSemaphore) {
                        // Wait for response from RTI
                        while (!_reservationComplete) {
                            try {
                                _reservationSemaphore.wait();
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                    if (!_reservationSucceeded) {
                        System.out.println("Name already taken, try again.");
                        return;
                    }
                } catch (IllegalName e) {
                    //System.out.println("Illegal name. Try again.");
                } catch (RTIexception e) {
                    //System.out.println("RTI exception when reserving name: " + e.getMessage());
                    return;
                }
            } while (!_reservationSucceeded);

            _userId = _rtiAmbassador.registerObjectInstance(participantId, _username);
            */

            _rtiAmbassador.enableAsynchronousDelivery();
            String SYNC_POINT = "ReadyToRun";
            byte[] tag = {};
            try {
                _rtiAmbassador.registerFederationSynchronizationPoint(SYNC_POINT, tag);
            } catch (Exception e) {

            }
            try {
                _rtiAmbassador.synchronizationPointAchieved(SYNC_POINT);
            } catch (RTIexception e) {

            }
            while(true) {
                try {
                    //HLAunicodeString nameEncoder = _encoderFactory.createHLAunicodeString(_username);
                    String message = "Hello";

                    ParameterHandleValueMap parameters = _rtiAmbassador.getParameterHandleValueMapFactory().create(1);
                    HLAunicodeString messageEncoder = _encoderFactory.createHLAunicodeString();
                    messageEncoder.setValue(message);
                    parameters.put(_parameterIdText, messageEncoder.toByteArray());
                    //parameters.put(_parameterIdSender, nameEncoder.toByteArray());
                    timeToMoveTo = timeToMoveTo.add(advancedStep);
                    _rtiAmbassador.nextMessageRequest(timeToMoveTo);
                    _rtiAmbassador.sendInteraction(_messageId, parameters, null, timeToMoveTo.add(advancedStep));
                } catch (RTIexception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {

        }
    }

    public void destroy() {
        try {
            _rtiAmbassador.resignFederationExecution(ResignAction.DELETE_OBJECTS_THEN_DIVEST);
            try {
                _rtiAmbassador.destroyFederationExecution(federateAttributes.getFederation());
            } catch (FederatesCurrentlyJoined ignored) {
            }
            _rtiAmbassador.disconnect();
            _rtiAmbassador = null;
            //timer.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters,
                                   byte[] userSuppliedTag,
                                   OrderType sentOrdering,
                                   TransportationTypeHandle theTransport,
                                   SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        if (interactionClass.equals(_messageId)) {
            if (!theParameters.containsKey(_parameterIdText)) {
                System.out.println("Bad message received: No text.");
                return;
            }
            if (!theParameters.containsKey(_parameterIdSender)) {
                System.out.println("Bad message received: No sender.");
                return;
            }
            try {
                HLAunicodeString messageDecoder = _encoderFactory.createHLAunicodeString();
                HLAunicodeString senderDecoder = _encoderFactory.createHLAunicodeString();
                messageDecoder.decode(theParameters.get(_parameterIdText));
                senderDecoder.decode(theParameters.get(_parameterIdSender));
                String message = messageDecoder.getValue();
                String sender = senderDecoder.getValue();

                System.out.println(sender + ": " + message);
                System.out.print("> ");
            } catch (DecoderException e) {
                System.out.println("Failed to decode incoming interaction");
            }
        }
    }

    @Override
    public final void objectInstanceNameReservationSucceeded(String objectName) {
        synchronized (_reservationSemaphore) {
            _reservationComplete = true;
            _reservationSucceeded = true;
            _reservationSemaphore.notifyAll();
        }
    }

    @Override
    public final void objectInstanceNameReservationFailed(String objectName) {
        synchronized (_reservationSemaphore) {
            _reservationComplete = true;
            _reservationSucceeded = false;
            _reservationSemaphore.notifyAll();
        }
    }

    @Override
    public void removeObjectInstance(ObjectInstanceHandle theObject,
                                     byte[] userSuppliedTag,
                                     OrderType sentOrdering,
                                     SupplementalRemoveInfo removeInfo) {
        Participant member = _knownObjects.remove(theObject);
        if (member != null) {
            System.out.println("[" + member + " has left]");
        }
    }

    @Override
    public void reflectAttributeValues(ObjectInstanceHandle theObject,
                                       AttributeHandleValueMap theAttributes,
                                       byte[] userSuppliedTag,
                                       OrderType sentOrdering,
                                       TransportationTypeHandle theTransport,
                                       SupplementalReflectInfo reflectInfo) {
        if (!_knownObjects.containsKey(theObject)) {
            if (theAttributes.containsKey(_attributeIdName)) {
                try {
                    final HLAunicodeString usernameDecoder = _encoderFactory.createHLAunicodeString();
                    usernameDecoder.decode(theAttributes.get(_attributeIdName));
                    String memberName = usernameDecoder.getValue();
                    Participant member = new Participant(memberName);
                    System.out.println("[" + member + " has joined]");
                    System.out.print("> ");
                    _knownObjects.put(theObject, member);
                } catch (DecoderException e) {
                    System.out.println("Failed to decode incoming attribute");
                }
            }
        }
    }

    @Override
    public final void provideAttributeValueUpdate(ObjectInstanceHandle theObject,
                                                  AttributeHandleSet theAttributes,
                                                  byte[] userSuppliedTag) {
        if (theObject.equals(_userId) && theAttributes.contains(_attributeIdName)) {
            try {
                AttributeHandleValueMap attributeValues = _rtiAmbassador.getAttributeHandleValueMapFactory().create(1);
                HLAunicodeString nameEncoder = _encoderFactory.createHLAunicodeString(_username);
                attributeValues.put(_attributeIdName, nameEncoder.toByteArray());
                _rtiAmbassador.updateAttributeValues(_userId, attributeValues, null);
            } catch (RTIexception ignored) {
            }
        }
    }

    @Override
    public void timeAdvanceGrant(LogicalTime logicalTime) {
    }
}
