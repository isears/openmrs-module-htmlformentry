package org.openmrs.module.htmlformentry;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.Encounter;
import org.openmrs.FormField;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.action.ObsGroupAction;
import org.openmrs.module.htmlformentry.element.DrugOrderSubmissionElement;
import org.openmrs.module.htmlformentry.element.ObsSubmissionElement;
import org.openmrs.propertyeditor.ConceptEditor;
import org.openmrs.propertyeditor.LocationEditor;
import org.openmrs.propertyeditor.PatientEditor;
import org.openmrs.propertyeditor.PersonEditor;
import org.openmrs.propertyeditor.UserEditor;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** 
 * HTML Form Entry utility methods
 */
public class HtmlFormEntryUtil {
	
	public static Log log = LogFactory.getLog(HtmlFormEntryUtil.class);
    
	/**
	 * Returns the HTML Form Entry service from the Context
	 * 
	 * @return HTML Form Entry service
	 */
    public static HtmlFormEntryService getService() {
        return Context.getService(HtmlFormEntryService.class);
    }

    /**
     * Fetches a http request parameter from an http request and returns it as a specific type
     * 
     * @param request an http servlet request
     * @param name the name of the parameter to retrieve
     * @param clazz the type to convert the parameter to
     * @return parameter, converted to appropriate type
     */
    public static Object getParameterAsType(HttpServletRequest request, String name, Class<?> clazz) {
        String val = request.getParameter(name);
        return convertToType(val, clazz);
    }
    
    /**
     * Converts a string to specified type
     * 
     * @param val the string to convert
     * @param clazz the type to convert to
     * @return an instance of the specified type, with it's value set to val
     */
    public static Object convertToType(String val, Class<?> clazz) {
        if (val == null)
            return null;
        if ("".equals(val) && !String.class.equals(clazz))
            return null;
        if (Location.class.isAssignableFrom(clazz)) {
            LocationEditor ed = new LocationEditor();
            ed.setAsText(val);
            return ed.getValue();
        } else if (User.class.isAssignableFrom(clazz)) {
            UserEditor ed = new UserEditor();
            ed.setAsText(val);
            return ed.getValue();
        } else if (Date.class.isAssignableFrom(clazz)) {
            try {
                DateFormat df = Context.getDateFormat();
                df.setLenient(false);
                return df.parse(val);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
        } else if (Double.class.isAssignableFrom(clazz)) {
            return Double.valueOf(val);
        } else if (Integer.class.isAssignableFrom(clazz)) {
        	return Integer.valueOf(val);
        } else if (Concept.class.isAssignableFrom(clazz)) {
            ConceptEditor ed = new ConceptEditor();
            ed.setAsText(val);
            return ed.getValue();
        } else if (Patient.class.isAssignableFrom(clazz)) {
        	PatientEditor ed = new PatientEditor();
        	ed.setAsText(val);
        	return ed.getValue();
        } else if (Person.class.isAssignableFrom(clazz)) {
        	PersonEditor ed = new PersonEditor();
        	ed.setAsText(val);
        	return ed.getValue();
        } else {
            return val;
        }
    }

    /**
     * Creaets an OpenMRS Obs instance
     * 
     * @param formField the form field that specifies the concept associated with the Obs
     * @param value value associated with the Obs
     * @param datetime date/time associated with the Obs (may be null)
     * @param accessionNumber accession number associatd with the Obs (may be null)
     * @return the created Obs instance
     */
    
    public static Obs createObs(FormField formField, Object value, Date datetime, String accessionNumber) {
        Concept concept = formField.getField().getConcept();
        if (concept == null)
            throw new FormEntryException("Can't create an Obs for a formField that doesn't represent a Concept");
        return createObs(concept, value, datetime, accessionNumber);
    }
    
    /**
     * Creates an OpenMRS Obs instance
     * 
     * @param concept concept associated with the Obs
     * @param value value associated with the Obs
     * @param datetime date/time associated with the Obs (may be null)
     * @param accessionNumber accession number associatd with the Obs (may be null)
     * @return the created Obs instance
     */
    public static Obs createObs(Concept concept, Object value, Date datetime, String accessionNumber) {
        Obs obs = new Obs();
        obs.setConcept(concept);
        ConceptDatatype dt = obs.getConcept().getDatatype();
        if (dt.isNumeric()) {
            obs.setValueNumeric(Double.parseDouble(value.toString()));
        } else if (dt.isText()) {
            if (value instanceof Location) {
            	Location location = (Location) value;
            	obs.setValueText(location.getId().toString() + " - " + location.getName());
            } 
            else if (value instanceof Person) {
            	Person person = (Person) value;
            	obs.setValueText(person.getId().toString() + " - " + person.getPersonName().toString());
            }
            else {
                obs.setValueText(value.toString());
            }
        } else if (dt.isCoded()) {
            if (value instanceof Concept)
                obs.setValueCoded((Concept) value);
            else
                obs.setValueCoded((Concept) convertToType(value.toString(), Concept.class));
        } else if (dt.isBoolean()) {
            boolean booleanValue = value != null && !Boolean.FALSE.equals(value) && !"false".equals(value);
            obs.setValueNumeric(booleanValue ? 1.0 : 0.0);
        } else if (ConceptDatatype.DATE.equals(dt.getHl7Abbreviation()) ||
        		ConceptDatatype.TIME.equals(dt.getHl7Abbreviation()) ||
        		ConceptDatatype.DATETIME.equals(dt.getHl7Abbreviation())) {
            Date date = (Date) value;
            obs.setValueDatetime(date);
        } else if ("ZZ".equals(dt.getHl7Abbreviation())) {
            // don't set a value
        } else {
            throw new IllegalArgumentException("concept datatype not yet implemented: " + dt.getName() + " with Hl7 Abbreviation: " + dt.getHl7Abbreviation());
        }
        if (datetime != null)
            obs.setObsDatetime(datetime);
        if (accessionNumber != null)
            obs.setAccessionNumber(accessionNumber);
        return obs;
    }
    
    /**
     * Converts an xml string to a Document object
     * 
     * @param xml the xml string to convert
     * @return the resulting Document object
     * @throws Exception
     */
    public static Document stringToDocument(String xml) throws Exception {
    	try {
	        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        Document document = db.parse(new InputSource(new StringReader(xml)));
	        return document;
    	}
    	catch (Exception e) {
    		log.error("Error converting String to Document:\n" + xml);
    		throw e;
    	}
    }
    
    /**
     * Converts a Document object to an xml string
     * 
     * @param document the Document instance to convert
     * @return the resulting xml string
     * @throws Exception
     */
    public static String documentToString(Document document) throws Exception {
        //set up a transformer
        Transformer trans = null;
        TransformerFactory transfac = TransformerFactory.newInstance();
        
        try {
            trans = transfac.newTransformer();
        } catch (TransformerException te) {
            System.out.println(HtmlFormEntryConstants.ERROR_TRANSFORMER_1 + te); 
        }
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, HtmlFormEntryConstants.CONSTANT_YES);
        trans.setOutputProperty(OutputKeys.INDENT, HtmlFormEntryConstants.CONSTANT_YES);
        trans.setOutputProperty(OutputKeys.METHOD, HtmlFormEntryConstants.CONSTANT_XML);
        
        //create string from xml tree
        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        DOMSource source = new DOMSource(document);
        try {
            trans.transform(source, result);            
        } catch (TransformerException te) {
            System.out.println(HtmlFormEntryConstants.ERROR_TRANSFORMER_2 + te); 
        }
        String xmlString = sw.toString();
        
        return xmlString;
    }

    /** 
     * Retrieves a child Node by name
     * 
     * @param content the parent Node
     * @param name the name of the child Node
     * @return the child Node with the specified name
     */
    public static Node findChild(Node content, String name) {
        if (content == null)
            return null;
        NodeList children = content.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node node = children.item(i);
            if (name.equals(node.getNodeName()))
                return node;
        }
        return null;
    }
    
    /**
     * Returns all the attributes associated with a Node
     * 
     * @param node the Node to retrieve attributes from
     * @return a Map containing all the attributes of the Node
     */
    public static Map<String, String> getNodeAttributes(Node node) {
    	Map<String, String> ret = new HashMap<String, String>();
        NamedNodeMap atts = node.getAttributes();
        for (int i=0; i<atts.getLength(); i++) {
            Node attribute = atts.item(i);
            ret.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        return ret;
    }
    
    /**
     * Returns a specific attribute of a Node
     * 
     * @param node the Node to retrieve the attribute from
     * @param attributeName the name of the attribute to return
     * @param defaultVal a default value to return if the attribute is not specified for the selected Node
     * @return
     */
    public static String getNodeAttribute(Node node, String attributeName, String defaultVal) {
    	String ret = getNodeAttributes(node).get(attributeName);
    	return (ret == null ? defaultVal : ret);
    }

    /** Creates a non-persistent "Fake" Person (used when previewing or validating an HTML Form)
     * 
     * @return the "fake" person
     */
    public static Patient getFakePerson() {
        Patient demo = new Patient(12345);
        demo.addName(new PersonName("Demo", "The", "Person"));
        Location l = Context.getLocationService().getAllLocations().iterator().next();
        for (PatientIdentifierType pit : Context.getPatientService().getAllPatientIdentifierTypes()) {
        	if (StringUtils.isEmpty(pit.getValidator())) {
        		demo.addIdentifier(new PatientIdentifier("Testing" + pit.getName() + "123", pit, l));
        	}
        }
        demo.setGender("F");
        demo.setUuid("testing-html-form-entry");
        {
	    	Calendar cal = Calendar.getInstance();
	    	cal.add(Calendar.YEAR, -31);
	        demo.setBirthdate(cal.getTime());
        }

        for (PersonAttributeType type : Context.getPersonService().getAllPersonAttributeTypes()) {
        	if (type.getFormat().equals("java.lang.String")) {
        		demo.addAttribute(new PersonAttribute(type, "Test "+type.getName()+" Attribute"));
        	}
        }
        PersonAddress addr = new PersonAddress();
        addr.setCityVillage("Rwinkwavu");
        addr.setCountyDistrict("Kayonza District");
        addr.setStateProvince("Eastern Province");
        addr.setCountry("Rwanda");
        demo.addAddress(addr);
        return demo;
    }

    /**
     * Combines a Date object that contains only a date component (day, month, year) with a Date object that contains
     * only a time component (hour, minute, second) into a single Date object
     * 
     * @param date the Date object that contains date information
     * @param time the Date object that contains time information
     * @return a Date object with the combined date/time
     */
	public static Date combineDateAndTime(Date date, Date time) {
		if (date == null)
			return null;
	    Calendar cal = Calendar.getInstance();
	    
	    cal.setTime(date);
	    if (time != null) {	    	
	    	Calendar temp = Calendar.getInstance();
	    	temp.setTime(time);
	    	cal.set(Calendar.HOUR_OF_DAY, temp.get(Calendar.HOUR_OF_DAY));
	    	cal.set(Calendar.MINUTE, temp.get(Calendar.MINUTE));
	    	cal.set(Calendar.SECOND, temp.get(Calendar.SECOND));
	    	cal.set(Calendar.MILLISECOND, temp.get(Calendar.MILLISECOND));
	    }
	    return cal.getTime();
    }
    
	/***
	 * Get the concept by id,
	 * the id can either be 
	 * 		1)an integer id like 5090 
	 * 	 or 2)mapping type id like "XYZ:HT"
	 * 	 or 3)uuid like "a3e12268-74bf-11df-9768-17cfc9833272"
	 * @param Id
	 * @return the concept if exist, else null
	 * @should find a concept by its conceptId 
     * @should find a concept by its mapping 
     * @should find a concept by its uuid
     * @should return null otherwise
	 */
	public static Concept getConcept(String id){
		Concept cpt = null;
		
		if (id != null){
			
			// see if this is a parseable int; if so, try looking up concept by id
			try { //handle integer: id
				int conceptId = Integer.parseInt(id);
				cpt  = Context.getConceptService().getConcept(conceptId);	
				
				if (cpt != null) {
					return cpt;
				} 
			} catch (Exception ex){
				//do nothing 
			}
			
			// handle  mapping id: xyz:ht
			int index = id.indexOf(":");
			if(index != -1){
				String mappingCode = id.substring(0,index).trim();
				String conceptCode = id.substring(index+1,id.length()).trim();	
				cpt = Context.getConceptService().getConceptByMapping(conceptCode,mappingCode);
				
				if (cpt != null) {
					return cpt;
				} 
			}
			
			//handle uuid id: "a3e1302b-74bf-11df-9768-17cfc9833272", if the id matches a uuid format
			if(isValidUuidFormat(id)){
				cpt = Context.getConceptService().getConceptByUuid(id);
			}
		}
		
		return cpt;
	}
	
	/**
	 * Gets a concept by id, mapping, or uuid. (See #getConcept(String) for precise details.)
	 * If no concept is found, throws an IllegalArgumentException with the given message. 
	 * @param id
	 * @param errorMessageIfNotFound
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static Concept getConcept(String id, String errorMessageIfNotFound) throws IllegalArgumentException {
		Concept c = null;
		try {
			c = getConcept(id);
		} catch (Exception ex) {
			throw new IllegalArgumentException(errorMessageIfNotFound, ex);
		}
		if (c == null)
			throw new IllegalArgumentException(errorMessageIfNotFound);
		return c;
	}
	
	/***
	 * Get the location by: 
	 * 		1)an integer id like 5090 
	 *   or 2) uuid like "a3e12268-74bf-11df-9768-17cfc9833272"
	 *   or 3) location name like "Boston"
 	 *   or 4) an id/name pair like "501 - Boston"  (this format is used when saving a location on a obs as a value text)
	 * @param Id
	 * @return the location if exist, else null
	 * @should find a location by its locationId
	 * @should find a location by name
     * @should find a location by its uuid
     * @should return null otherwise
	 */
	public static Location getLocation(String id){
		
		Location location = null;
		
		if(id != null){
			
			// see if this is parseable int; if so, try looking up by id
			try { //handle integer: id
				int locationId = Integer.parseInt(id);
				location = Context.getLocationService().getLocation(locationId);
				
				if (location != null) {
					return location;
				}
			} catch (Exception ex){
				//do nothing 
			}
			
			// handle uuid id: "a3e1302b-74bf-11df-9768-17cfc9833272" if id matches a uuid format
			if(isValidUuidFormat(id)){
				location  = Context.getLocationService().getLocationByUuid(id);
				
				if (location != null) {
					return location;
				}
			}
			
			// if it's neither a uuid or id, try location name
			location = Context.getLocationService().getLocation(id);
			
			if (location != null) {
				return location;
			}
			
			// try the "101 - Cange" case
			if (id.contains(" ")) {
				String [] values = id.split(" ");
				try{ 
					int locationId = Integer.parseInt(values[0]);
					location = Context.getLocationService().getLocation(locationId);
					
					if (location != null) {
						return location;
					}
				} catch (Exception ex){
					//do nothing 
				}
			}
		}
		
		// no match found, so return null
		return null;
	}
	
	/***
	 * Get the program by: 
	 * 		1)an integer id like 5090 
	 *   or 2) uuid like "a3e12268-74bf-11df-9768-17cfc9833272"
	 *   or 3) name of *associated concept* (not name of program), like "MDR-TB Program"
	 *   	
	 * @param Id
	 * @return the program if exist, else null
	 * @should find a program by its id
	 * @should find a program by name of associated concept
     * @should find a program by its uuid
     * @should return null otherwise
	 */
	public static Program getProgram(String id){
		
		Program program = null;
		
		if(id != null){
			
			// see if this is parseable int; if so, try looking up by id
			try{//handle integer: id
				int programId = Integer.parseInt(id);
				program = Context.getProgramWorkflowService().getProgram(programId);
				
				if (program != null) {
					return program;
				}
			} catch (Exception ex){
				//do nothing 
			}
			
			//handle uuid id: "a3e1302b-74bf-11df-9768-17cfc9833272", if id matches uuid format
			if(isValidUuidFormat(id)){
				program = Context.getProgramWorkflowService().getProgramByUuid(id);
				
				if (program != null) {
					return program;
				}
			}
			else {
				// if it's neither a uuid or id, try program name
				// (note that this API method actually checks based on name of the associated concept, not the name of the program itself)
				program = Context.getProgramWorkflowService().getProgramByName(id);			
			}
			
		}
		return program;
	}
	
	/***
	 * Get the person by: 
	 * 		1)an integer id like 5090 
	 *   or 2) uuid like "a3e12268-74bf-11df-9768-17cfc9833272"
	 *   or 3) a username like "mgoodrich"
	 *   or 4) an id/name pair like "5090 - Bob Jones"  (this format is used when saving a person on a obs as a value text)
	 * @param Id
	 * @return the person if exist, else null
	 * @should find a person by its id
     * @should find a person by its uuid
     * @should find a person by username of corresponding user
     * @should return null otherwise
	 */
	public static Person getPerson(String id){
		
		Person person = null;
		
		if (id != null){
			
			// see if this is parseable int; if so, try looking up by id
			try{ //handle integer: id
				int personId = Integer.parseInt(id);
				person = Context.getPersonService().getPerson(personId);
				
				if (person != null) {
					return person;
				}
			} catch (Exception ex){
				//do nothing 
			}
			
			// handle uuid id: "a3e1302b-74bf-11df-9768-17cfc9833272", if id matches uuid format
			if(isValidUuidFormat(id)){
				person  = Context.getPersonService().getPersonByUuid(id);
				
				if (person != null) {
					return person;
				}
			}
			
			// handle username
			User personByUsername = Context.getUserService().getUserByUsername(id);			
			if (personByUsername != null) {
				return personByUsername.getPerson();
			}
			
			// try the "5090 - Bob Jones" case
			if (id.contains(" ")) {
				String [] values = id.split(" ");
				try{ 
					int personId = Integer.parseInt(values[0]);
					person = Context.getPersonService().getPerson(personId);
					
					if (person != null) {
						return person;
					}
				} catch (Exception ex){
					//do nothing 
				}
			}
		}
		
		// no match found, so return null
		return null;
	}
	
	/***
	 * Get the patient identifier type by: 
	 * 		1)an integer id like 5090 
	 *   or 2) uuid like "a3e12268-74bf-11df-9768-17cfc9833272"
	 *   or 3) a name like "Temporary Identifier"
	 * @param Id
	 * @return the identifier type if exist, else null
	 * @should find an identifier type by its id
     * @should find an identifier type by its uuid
     * @should find an identifier type by its name
     * @should return null otherwise
	 */
	public static PatientIdentifierType getPatientIdentifierType(String id){
		PatientIdentifierType identifierType = null;
		
		if(id != null){
			
			// see if this is parseable int; if so, try looking up by id
			try{ //handle integer: id
				int identifierTypeId = Integer.parseInt(id);
				identifierType = Context.getPatientService().getPatientIdentifierType(identifierTypeId);
				
				if (identifierType != null) {
					return identifierType;
				}
			} catch (Exception ex){
				//do nothing 
			}
			
			//handle uuid id: "a3e1302b-74bf-11df-9768-17cfc9833272", if id matches uuid format
			if(isValidUuidFormat(id)){
				identifierType  = Context.getPatientService().getPatientIdentifierTypeByUuid(id);
				
				if (identifierType != null) {
					return identifierType;
				}
			}
			// handle name
			else {
				// if it's neither a uuid or id, try identifier type name
				identifierType = Context.getPatientService().getPatientIdentifierTypeByName(id);	
			}
		}
		return identifierType;
	}
	
	
	/***
	 * Determines if the passed string is in valid uuid format
	 * By OpenMRS standards, a uuid must be 36 characters in length and not contain whitespace, but
	 * we do not enforce that a uuid be in the "canonical" form, with alphanumerics
	 * seperated by dashes, since the MVP dictionary does not use this format
	 * (We also are being slightly lenient and accepting uuids that are 37 or 38 characters in
	 * length, since the uuid data field is 38 characters long)
	 */
	public static boolean isValidUuidFormat(String uuid) {
		if (uuid.length() < 36 || uuid.length() > 38 || uuid.contains(" ")) {
			return false;
		}
		
		return true;
	}
	
	public static List<PatientIdentifierType> getPatientIdentifierTypes(){
		return Context.getPatientService().getAllPatientIdentifierTypes();
	}
	
	
	/**
	 * Utility method that sets all matched obs and orders to voided, and voids encounter if all obs and orders in encounter are voided.
	 * 
	 * Does not call save, just updates the voided fields on all objects in encounter
	 * Uses a 'dummy' FormEntrySession to use htmlformentry schema matching mechanism, and then examines the leftover Obs, Orders from the FormEntrySession constructor
	 * 
	 * 
	 * @param session
	 */
	public static void voidEncounterByHtmlFormSchema(Encounter e, HtmlForm htmlform, String voidReason) throws Exception {
		if (e != null && htmlform != null){
			if (voidReason == null)
				voidReason = "htmlformentry";
		    boolean shouldVoidEncounter = true;
		    Map<Obs,Obs> replacementObs = new HashMap<Obs,Obs>();//new, then source
		    Map<Order,Order> replacementOrders = new HashMap<Order,Order>();//new, then source
		    Encounter eTmp = returnEncounterCopy(e,replacementObs, replacementOrders);
			FormEntrySession session = new FormEntrySession(eTmp.getPatient(), eTmp, Mode.VIEW, htmlform);
			List<FormSubmissionControllerAction> actions = session.getSubmissionController().getActions();
			Set<Obs> matchedObs = new HashSet<Obs>();
			Set<Order> matchedOrders = new HashSet<Order>();
			for (FormSubmissionControllerAction lfca : actions){
				if (lfca instanceof ObsSubmissionElement){
					ObsSubmissionElement ose = (ObsSubmissionElement) lfca;
					if (ose.getExistingObs() != null){
						matchedObs.add(ose.getExistingObs());
					}	
				}	
				if (lfca instanceof ObsGroupAction){
					ObsGroupAction oga = (ObsGroupAction) lfca;
					if (oga.getExistingGroup() != null){
						matchedObs.add(oga.getExistingGroup());						
					}	
				}
				if (lfca instanceof DrugOrderSubmissionElement){
					DrugOrderSubmissionElement dse = (DrugOrderSubmissionElement) lfca;
					if (dse.getExistingOrder() != null){
						matchedOrders.add(dse.getExistingOrder());
					}
				}
			}

			for (Obs o : e.getAllObs(false)){ //ignore voided obs
				boolean matched = false;
				for (Obs oMatched : matchedObs){
					if (replacementObs.get(oMatched) != null && replacementObs.get(oMatched).equals(o)){
						o.setVoided(true);
						o.setVoidedBy(Context.getAuthenticatedUser());
						o.setVoidReason(voidReason);
						o.setDateVoided(new Date());
						matched = true;
						break;
					}
				}
				if (!matched)
					shouldVoidEncounter = false;
			}
			
			for (Order o : e.getOrders()){
				if (!o.isVoided()){ //ignore voided orders
					boolean matched = false;
					for (Order oMatched : matchedOrders){
						//Order.equals only checks Id value
						if (replacementOrders.get(oMatched) != null && replacementOrders.get(oMatched).equals(o) && !o.isVoided()){
							o.setVoided(true);
							o.setVoidedBy(Context.getAuthenticatedUser());
							o.setVoidReason(voidReason);
							o.setDateVoided(new Date());
							matched = true;
							break;
						}
					}
					if (!matched)
						shouldVoidEncounter = false;
				}
			}
			
	        if (shouldVoidEncounter){
	        	e.setVoided(true);
	        	e.setVoidedBy(Context.getAuthenticatedUser());
	        	e.setVoidReason(voidReason);
	        	e.setDateVoided(new Date());
	        }
	        eTmp = null;
		}
	}

	/**
	 * Method that returns a copy of an Encounter.  Includes copies of all Obs tree structures and Orders.
	 * 
	 * @param source
	 * @param replacementObs
	 * @param replacementOrders
	 * @return
	 * @throws Exception
	 */
	private static Encounter returnEncounterCopy(Encounter source, Map<Obs,Obs> replacementObs, Map<Order,Order> replacementOrders) throws Exception {
		if (source != null){
			Encounter encNew = new Encounter();
			encNew.setChangedBy(source.getChangedBy());
			encNew.setCreator(source.getCreator());
			encNew.setDateChanged(source.getDateChanged());
			encNew.setDateCreated(source.getDateCreated());
			encNew.setDateVoided(source.getDateVoided());
			encNew.setEncounterDatetime(source.getEncounterDatetime());
			encNew.setEncounterId(source.getEncounterId());
			encNew.setEncounterType(source.getEncounterType());
			encNew.setForm(source.getForm());
			encNew.setLocation(source.getLocation());
			encNew.setPatient(source.getPatient());
			encNew.setProvider(source.getProvider());
			encNew.setVoided(source.getVoided());
			encNew.setVoidedBy(source.getVoidedBy());
			encNew.setVoidReason(source.getVoidReason());

			//note: we can do this because we're not going to manipulate anything about these obs or orders, and this copy won't be persisted...

			Set<Obs> newObs = new HashSet<Obs>();
			for (Obs o :source.getAllObs(true)){
				Obs oNew = cloneObs(o, replacementObs);
				newObs.add(oNew);
			}
			encNew.setObs(newObs);
			
			Set<Order> newOrders = new HashSet<Order>();
			for (Order o : source.getOrders()){
				Order oNew = (Order) returnOrderCopy(o, replacementOrders);
				newOrders.add(oNew);
			}
			encNew.setOrders(newOrders);
			return encNew;
		}
		return null;
	}
	
	/**
	 * Returns a copy of an Obs.  Recurses through GroupMembers to return copies of those also, so the whole Obs tree is a copy.
	 * 
	 * @param obsToCopy
	 * @param replacements
	 * @return
	 */
	private static Obs cloneObs(Obs obsToCopy, Map<Obs, Obs> replacements){
		Obs newObs = new Obs(obsToCopy.getPerson(), obsToCopy.getConcept(), obsToCopy.getObsDatetime(), obsToCopy
		        .getLocation());		
		newObs.setObsGroup(obsToCopy.getObsGroup());
		newObs.setAccessionNumber(obsToCopy.getAccessionNumber());
		newObs.setValueCoded(obsToCopy.getValueCoded());
		newObs.setValueDrug(obsToCopy.getValueDrug());
		newObs.setValueGroupId(obsToCopy.getValueGroupId());
		newObs.setValueDatetime(obsToCopy.getValueDatetime());
		newObs.setValueNumeric(obsToCopy.getValueNumeric());
		newObs.setValueModifier(obsToCopy.getValueModifier());
		newObs.setValueText(obsToCopy.getValueText());
		newObs.setComment(obsToCopy.getComment());
		newObs.setOrder(obsToCopy.getOrder());
		newObs.setEncounter(obsToCopy.getEncounter());
		newObs.setDateStarted(obsToCopy.getDateStarted());
		newObs.setDateStopped(obsToCopy.getDateStopped());
		newObs.setCreator(obsToCopy.getCreator());
		newObs.setDateCreated(obsToCopy.getDateCreated());
		newObs.setVoided(obsToCopy.getVoided());
		newObs.setVoidedBy(obsToCopy.getVoidedBy());
		newObs.setDateVoided(obsToCopy.getDateVoided());
		newObs.setVoidReason(obsToCopy.getVoidReason());
		
		newObs.setValueComplex(obsToCopy.getValueComplex());
		newObs.setComplexData(obsToCopy.getComplexData());
		if (obsToCopy.isObsGrouping()){
			newObs.setGroupMembers(null);
			for (Obs oinner : obsToCopy.getGroupMembers()){
				Obs oinnerNew = cloneObs(oinner, replacements);
				newObs.addGroupMember(oinnerNew);
			}
		}
		replacements.put(newObs, obsToCopy );
		return newObs;
	}
	
	/**
	 * 
	 * Utility to return a copy of an Order.  Uses reflection so that this code will support any subclassing of Order, such as DrugOrder 
	 * 
	 * @param source
	 * @param replacementOrders
	 * @return A copy of an Order
	 * @throws Exception
	 */
	private static Object returnOrderCopy(Order source, Map<Order,Order> replacementOrders) throws Exception {
		Class<? extends Object> clazz = source.getClass();
		Object ret = clazz.newInstance();
		Set<String> fieldNames = new HashSet<String>();
		List<Field>   fields = new ArrayList<Field>();
		addSuperclassFields(fields, clazz);		
		for (Field f : fields){
			fieldNames.add(f.getName());
		}
		for (String root:fieldNames){
			for (Method getter : clazz.getMethods()){
				if (getter.getName().toUpperCase().equals("GET" + root.toUpperCase()) && getter.getParameterTypes().length == 0){
					Method setter = getMethodCaseInsensitive(clazz, "SET" + root.toUpperCase());
					if (setter != null && methodsSupportSameArgs(getter, setter)){
						Object o = getter.invoke(source, Collections.EMPTY_LIST.toArray());
						if (o != null){
								//NOTE:  there are no collections in Order or DrugOrder.  This gets nastier if so...
								setter.invoke(ret, o);
						}
					}
				}
			}
		}
		replacementOrders.put((Order) ret, source);
		return ret ;
	}
	
	/**
	 * Performs a case insensitive search on a class for a method by name.
	 * @param clazz
	 * @param methodName
	 * @return the found Method
	 */
	private static Method getMethodCaseInsensitive(Class<? extends Object> clazz, String methodName){
		for (Method m : clazz.getMethods()){
			if (m.getName().toUpperCase().equals(methodName.toUpperCase())){
				return m;
			}
		}
		return null;
	}
	
	/**
	 * compares getter return types to setter parameter types.
	 * @param getter
	 * @param setter
	 * @return true if getter return types are the same as setter parameter types.  Else false.
	 */
	private static boolean methodsSupportSameArgs(Method getter, Method setter){
			if (getter != null && setter != null 
				&& setter.getParameterTypes() != null 
				&& setter.getParameterTypes().length == 1
				&& getter.getReturnType() != null
				&& getter.getReturnType().equals(setter.getParameterTypes()[0]))
			return true;
		return false;
	}
	
	/**
	 * recurses through all superclasses of a class and adds the fields from that superclass
	 * @param fields
	 * @param clazz
	 */
	private static void addSuperclassFields(List<Field> fields, Class<? extends Object> clazz){
		fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
		if (clazz.getSuperclass() != null){
			addSuperclassFields(fields, clazz.getSuperclass());
		}	
	}
	
	
}