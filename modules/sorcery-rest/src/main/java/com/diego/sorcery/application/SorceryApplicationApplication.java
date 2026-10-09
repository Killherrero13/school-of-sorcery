package com.diego.sorcery.application;

import com.diego.sorcery.model.SorceryModels.*;
import com.diego.sorcery.service.SelectionService;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

@Component(
    property = {
        JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/sorcery",
        JaxrsWhiteboardConstants.JAX_RS_NAME + "=Sorcery.Rest"
    },
    service = Application.class
)
@Produces("application/json")
public class SorceryApplicationApplication extends Application {

    private final SelectionService selectionService = new SelectionService();

    private volatile SelectionResult currentResult;

    @Override
    public Set<Object> getSingletons() {
        return Collections.singleton(this);
    }

    @POST
    @Path("/selections")
    @Consumes("application/json")
    public synchronized Response select(String body) {
        try {
            JSONObject request = JSONFactoryUtil.createJSONObject(body);

            List<Applicant> applicants = readApplicants(
                array(request, "applications"));

            CouncilRules rules = readRules(object(request, "councilRules"));

            SelectionResult result = selectionService.select(applicants, rules);

            // Prepare the response before publishing the new state.
            String response = resultJson(result).toString();
            currentResult = result;

            return Response.ok(response).build();
        }
        catch (JSONException | IllegalArgumentException exception) {
            return error(400, exception.getMessage());
        }
    }

    @GET
    @Path("/houses")
    public Response houses() {
        SelectionResult result = currentResult;

        if (result == null) {
            return error(404, "No selection has been processed yet");
        }

        JSONObject response = JSONFactoryUtil.createJSONObject();
        response.put("year", result.year());
        response.put("houses", housesJson(result.houses()));

        return Response.ok(response.toString()).build();
    }

    private List<Applicant> readApplicants(JSONArray array) {
        List<Applicant> applicants = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.getJSONObject(i);

            if (value == null) {
                throw new IllegalArgumentException(
                    "Each application must be an object");
            }

            applicants.add(new Applicant(
                text(value, "id"),
                text(value, "firstName"),
                text(value, "familyName"),
                integer(value, "age"),
                strings(array(value, "virtues")),
                strings(array(value, "weaknesses")),
                date(value, "applicationDate")));
        }

        return applicants;
    }

    private CouncilRules readRules(JSONObject value) {
        JSONObject dates = object(value, "applicationDates");
        JSONObject ages = object(value, "ageRange");
        JSONArray houseArray = array(value, "houses");

        List<HouseRule> houses = new ArrayList<>();

        for (int i = 0; i < houseArray.length(); i++) {
            JSONObject house = houseArray.getJSONObject(i);

            if (house == null) {
                throw new IllegalArgumentException(
                    "Each house must be an object");
            }

            houses.add(new HouseRule(
                text(house, "name"),
                integer(house, "beds"),
                readPoints(object(house, "points"), false)));
        }

        return new CouncilRules(
            text(value, "year"),
            new DateRange(date(dates, "from"), date(dates, "to")),
            new AgeRange(integer(ages, "min"), integer(ages, "max")),
            integer(value, "places"),
            strings(array(value, "bannedFamilies")),
            strings(array(value, "unacceptableWeaknesses")),
            strings(array(value, "invitations")),
            readPoints(object(value, "points"), true),
            houses);
    }

    private Points readPoints(JSONObject value, boolean includeAge) {
        List<AgePoints> ages = new ArrayList<>();

        if (includeAge) {
            JSONArray groups = array(value, "age");

            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);

                if (group == null) {
                    throw new IllegalArgumentException(
                        "Each age points range must be an object");
                }

                ages.add(new AgePoints(
                    integer(group, "from"),
                    integer(group, "to"),
                    integer(group, "points")));
            }
        }

        return new Points(
            pointMap(object(value, "virtue")),
            pointMap(object(value, "family")),
            pointMap(object(value, "weakness")),
            ages);
    }

    private Map<String, Integer> pointMap(JSONObject value) {
        Map<String, Integer> points = new LinkedHashMap<>();

        for (String key : value.keySet()) {
            points.put(key, integer(value, key));
        }

        return points;
    }

    private List<String> strings(JSONArray values) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < values.length(); i++) {
            Object value = values.get(i);

            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(
                    "Array elements must be nonempty strings");
            }

            result.add(text);
        }

        return result;
    }

    private JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.getJSONObject(key);

        if (value == null) {
            throw new IllegalArgumentException(
                "Required object: " + key);
        }

        return value;
    }

    private JSONArray array(JSONObject parent, String key) {
        JSONArray value = parent.getJSONArray(key);

        if (value == null) {
            throw new IllegalArgumentException(
                "Required array: " + key);
        }

        return value;
    }

    private String text(JSONObject parent, String key) {
        Object value = parent.get(key);

        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
                "Required nonempty string: " + key);
        }

        return text;
    }

    private int integer(JSONObject parent, String key) {
        Object value = parent.get(key);

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                "Required integer: " + key);
        }

        try {
            return new java.math.BigDecimal(number.toString())
                .intValueExact();
        }
        catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                "Invalid integer: " + key);
        }
    }

    private LocalDate date(JSONObject parent, String key) {
        try {
            return LocalDate.parse(text(parent, key));
        }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                "Invalid date, expected YYYY-MM-DD: " + key);
        }
    }

    private JSONObject applicantJson(Applicant applicant) {
        JSONObject value = JSONFactoryUtil.createJSONObject();

        value.put("id", applicant.id());
        value.put("firstName", applicant.firstName());
        value.put("familyName", applicant.familyName());
        value.put("age", applicant.age());

        return value;
    }

    private JSONObject resultJson(SelectionResult result) {
        JSONObject response = JSONFactoryUtil.createJSONObject();
        JSONArray accepted = JSONFactoryUtil.createJSONArray();
        JSONArray rejected = JSONFactoryUtil.createJSONArray();

        for (AcceptedApplicant applicant : result.accepted()) {
            JSONObject value = applicantJson(applicant.applicant());

            value.put("position", applicant.position());
            value.put("admissionScore", applicant.admissionScore());
            value.put("invited", applicant.invited());
            value.put("house", applicant.house());
            value.put("houseScore", applicant.houseScore());

            accepted.put(value);
        }

        for (RejectedApplicant applicant : result.rejected()) {
            JSONObject value = applicantJson(applicant.applicant());

            value.put("reason", applicant.reason().name());

            if (applicant.position() != null) {
                value.put("position", applicant.position());
                value.put("admissionScore", applicant.admissionScore());
            }

            rejected.put(value);
        }

        response.put("year", result.year());
        response.put("acceptedCount", result.accepted().size());
        response.put("rejectedCount", result.rejected().size());
        response.put("accepted", accepted);
        response.put("rejected", rejected);
        response.put("houses", housesJson(result.houses()));

        return response;
    }

    private JSONArray housesJson(List<HouseState> houses) {
        JSONArray result = JSONFactoryUtil.createJSONArray();

        for (HouseState house : houses) {
            JSONObject value = JSONFactoryUtil.createJSONObject();
            JSONArray ids = JSONFactoryUtil.createJSONArray();

            for (String id : house.applicantIds()) {
                ids.put(id);
            }

            value.put("name", house.name());
            value.put("beds", house.beds());
            value.put("occupied", house.occupied());
            value.put("available", house.available());
            value.put("applicantIds", ids);

            result.put(value);
        }

        return result;
    }

    private Response error(int status, String message) {
        JSONObject response = JSONFactoryUtil.createJSONObject();

        response.put("error", message == null ? "Invalid request" : message);

        return Response.status(status)
            .entity(response.toString())
            .build();
    }
}