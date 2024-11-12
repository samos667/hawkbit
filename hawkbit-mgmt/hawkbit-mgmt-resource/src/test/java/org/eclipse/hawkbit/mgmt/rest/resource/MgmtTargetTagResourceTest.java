/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.mgmt.rest.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.eclipse.hawkbit.mgmt.rest.api.MgmtRestConstants;
import org.eclipse.hawkbit.mgmt.rest.api.MgmtTargetTagRestApi;
import org.eclipse.hawkbit.mgmt.rest.resource.util.ResourceUtility;
import org.eclipse.hawkbit.repository.event.remote.TargetTagDeletedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetTagUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.model.Tag;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.model.TargetTag;
import org.eclipse.hawkbit.repository.test.matcher.Expect;
import org.eclipse.hawkbit.repository.test.matcher.ExpectEvents;
import org.eclipse.hawkbit.rest.json.model.ExceptionInfo;
import org.eclipse.hawkbit.rest.util.JsonBuilder;
import org.eclipse.hawkbit.rest.util.MockMvcResultPrinter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Spring MVC Tests against the MgmtTargetTagResource.
 */
@Feature("Component Tests - Management API")
@Story("Target Tag Resource")
public class MgmtTargetTagResourceTest extends AbstractManagementApiIntegrationTest {

    private static final String TARGETTAGS_ROOT = "http://localhost" + MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/";
    private static final Random RND = new Random();

    @Test
    @Description("Verfies that a paged result list of target tags reflects the content on the repository side.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 2) })
    public void getTargetTags() throws Exception {
        final List<TargetTag> tags = testdataFactory.createTargetTags(2, "");
        final TargetTag assigned = tags.get(0);
        final TargetTag unassigned = tags.get(1);

        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(applyTagMatcherOnPagedResult(assigned)).andExpect(applyTagMatcherOnPagedResult(unassigned))
                .andExpect(applySelfLinkMatcherOnPagedResult(assigned, TARGETTAGS_ROOT + assigned.getId()))
                .andExpect(applySelfLinkMatcherOnPagedResult(unassigned, TARGETTAGS_ROOT + unassigned.getId()))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_TOTAL, equalTo(2)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_SIZE, equalTo(2)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_CONTENT, hasSize(2)));

    }

    @Test
    @Description("Handles the GET request of retrieving all targets tags within SP based by parameter")
    public void getTargetTagsWithParameters() throws Exception {
        final List<TargetTag> tags = testdataFactory.createTargetTags(2, "");
        final TargetTag assigned = tags.get(0);
        final TargetTag unassigned = tags.get(1);
        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "?limit=10&sort=name:ASC&offset=0&q=name==targetTag"))
                .andExpect(status().isOk()).andDo(MockMvcResultPrinter.print());
    }

    @Test
    @Description("Verifies that a page result when listing tags reflects on the content in the repository when filtered by 2 fields - one tag field and one target field")
    public void getTargetTagsFilteredByColor() throws Exception {
        final String controllerId1 = "controllerTestId1";
        final String controllerId2 = "controllerTestId2";
        testdataFactory.createTarget(controllerId1);
        testdataFactory.createTarget(controllerId2);

        final List<TargetTag> tags = testdataFactory.createTargetTags(2, "");
        final TargetTag tag1 = tags.get(0);
        final TargetTag tag2 = tags.get(1);

        targetManagement.assignTag(List.of(controllerId1, controllerId2), tag1.getId());
        targetManagement.assignTag(List.of(controllerId2), tag2.getId());

        // pass here q directly as a pure string because .queryParam method delimiters the parameters in q with ,
        // which is logical OR, we want AND here
        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING +
                        "?" + MgmtRestConstants.REQUEST_PARAMETER_SEARCH + "=colour==" + tag2.getColour())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(applyTagMatcherOnPagedResult(tag2))
                .andExpect(applySelfLinkMatcherOnPagedResult(tag2, TARGETTAGS_ROOT + tag2.getId()))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_TOTAL, equalTo(1)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_SIZE, equalTo(1)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_CONTENT, hasSize(1)));
    }

    @Test
    @Description("Verfies that a single result of a target tag reflects the content on the repository side.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 2) })
    public void getTargetTag() throws Exception {
        final List<TargetTag> tags = testdataFactory.createTargetTags(2, "");
        final TargetTag assigned = tags.get(0);

        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + assigned.getId())
                        .accept(MediaType.APPLICATION_JSON)).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(applyTagMatcherOnSingleResult(assigned))
                .andExpect(applySelfLinkMatcherOnSingleResult(TARGETTAGS_ROOT + assigned.getId()))
                .andExpect(jsonPath("_links.assignedTargets.href",
                        equalTo(TARGETTAGS_ROOT + assigned.getId() + "/assigned?offset=0&limit=50")));

    }

    @Test
    @Description("Verifies that created target tags are stored in the repository as send to the API.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 2) })
    public void createTargetTags() throws Exception {
        final Tag tagOne = entityFactory.tag().create().colour("testcol1").description("its a test1").name("thetest1")
                .build();
        final Tag tagTwo = entityFactory.tag().create().colour("testcol2").description("its a test2").name("thetest2")
                .build();

        final ResultActions result = mvc
                .perform(post(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING)
                        .content(JsonBuilder.tags(Arrays.asList(tagOne, tagTwo)))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        final Tag createdOne = targetTagManagement.findByRsql(PAGE, "name==thetest1").getContent().get(0);
        assertThat(createdOne.getName()).isEqualTo(tagOne.getName());
        assertThat(createdOne.getDescription()).isEqualTo(tagOne.getDescription());
        assertThat(createdOne.getColour()).isEqualTo(tagOne.getColour());
        final Tag createdTwo = targetTagManagement.findByRsql(PAGE, "name==thetest2").getContent().get(0);
        assertThat(createdTwo.getName()).isEqualTo(tagTwo.getName());
        assertThat(createdTwo.getDescription()).isEqualTo(tagTwo.getDescription());
        assertThat(createdTwo.getColour()).isEqualTo(tagTwo.getColour());

        result.andExpect(applyTagMatcherOnArrayResult(createdOne)).andExpect(applyTagMatcherOnArrayResult(createdTwo));
    }

    @Test
    @Description("Verifies that an updated target tag is stored in the repository as send to the API.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetTagUpdatedEvent.class, count = 1) })
    public void updateTargetTag() throws Exception {
        final List<TargetTag> tags = testdataFactory.createTargetTags(1, "");
        final TargetTag original = tags.get(0);

        final Tag update = entityFactory.tag().create().name("updatedName").colour("updatedCol")
                .description("updatedDesc").build();

        final ResultActions result = mvc
                .perform(put(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + original.getId())
                        .content(JsonBuilder.tag(update)).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        final Tag updated = targetTagManagement.findByRsql(PAGE, "name==updatedName").getContent().get(0);
        assertThat(updated.getName()).isEqualTo(update.getName());
        assertThat(updated.getDescription()).isEqualTo(update.getDescription());
        assertThat(updated.getColour()).isEqualTo(update.getColour());

        result.andExpect(applyTagMatcherOnArrayResult(updated)).andExpect(applyTagMatcherOnArrayResult(updated));
    }

    @Test
    @Description("Verfies that the delete call is reflected by the repository.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetTagDeletedEvent.class, count = 1) })
    public void deleteTargetTag() throws Exception {
        final List<TargetTag> tags = testdataFactory.createTargetTags(1, "");
        final TargetTag original = tags.get(0);

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + original.getId()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        assertThat(targetTagManagement.get(original.getId())).isNotPresent();
    }

    @Test
    @Description("Ensures that assigned targets to tag in repository are listed with proper paging results.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 5), @Expect(type = TargetUpdatedEvent.class, count = 5) })
    public void getAssignedTargets() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final int targetsAssigned = 5;
        final List<Target> targets = testdataFactory.createTargets(targetsAssigned);
        targetManagement.assignTag(targets.stream().map(Target::getControllerId).collect(Collectors.toList()), tag.getId());

        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned"))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_TOTAL, equalTo(targetsAssigned)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_SIZE, equalTo(targetsAssigned)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_CONTENT, hasSize(targetsAssigned)));
    }

    @Test
    @Description("Ensures that assigned DS to tag in repository are listed with proper paging results with paging limit parameter.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 5), @Expect(type = TargetUpdatedEvent.class, count = 5) })
    public void getAssignedTargetsWithPagingLimitRequestParameter() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final int targetsAssigned = 5;
        final int limitSize = 1;
        final List<Target> targets = testdataFactory.createTargets(targetsAssigned);
        targetManagement.assignTag(targets.stream().map(Target::getControllerId).collect(Collectors.toList()), tag.getId());

        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param(MgmtRestConstants.REQUEST_PARAMETER_PAGING_LIMIT, String.valueOf(limitSize)))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_TOTAL, equalTo(targetsAssigned)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_SIZE, equalTo(limitSize)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_CONTENT, hasSize(limitSize)));
    }

    @Test
    @Description("Ensures that assigned targets to tag in repository are listed with proper paging results with paging limit and offset parameter.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 5), @Expect(type = TargetUpdatedEvent.class, count = 5) })
    public void getAssignedTargetsWithPagingLimitAndOffsetRequestParameter() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final int targetsAssigned = 5;
        final int offsetParam = 2;
        final int expectedSize = targetsAssigned - offsetParam;

        final List<Target> targets = testdataFactory.createTargets(targetsAssigned);
        targetManagement.assignTag(targets.stream().map(Target::getControllerId).collect(Collectors.toList()), tag.getId());

        mvc.perform(get(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param(MgmtRestConstants.REQUEST_PARAMETER_PAGING_OFFSET, String.valueOf(offsetParam))
                        .param(MgmtRestConstants.REQUEST_PARAMETER_PAGING_LIMIT, String.valueOf(targetsAssigned)))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_TOTAL, equalTo(targetsAssigned)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_SIZE, equalTo(expectedSize)))
                .andExpect(jsonPath(MgmtTargetResourceTest.JSON_PATH_PAGED_LIST_CONTENT, hasSize(expectedSize)));
    }

    @Test
    @Description("Verifies that tag assignments done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 1),
            @Expect(type = TargetUpdatedEvent.class, count = 1) })
    public void assignTarget() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final Target assigned = testdataFactory.createTargets(1).get(0);

        mvc.perform(post(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned/" +
                assigned.getControllerId())).andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        final List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();
        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsOnly(assigned.getControllerId());
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2) })
    public void assignTargets() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<Target> targets = testdataFactory.createTargets(2);
        final Target assigned0 = targets.get(0);
        final Target assigned1 = targets.get(1);
        ;

        mvc.perform(put(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .content(JsonBuilder.toArray(Arrays.asList(assigned0.getControllerId(), assigned1.getControllerId())))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        final List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();
        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsOnly(assigned0.getControllerId(), assigned1.getControllerId());
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2) })
    public void assignTargetsNotFound() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        mvc.perform(put(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isNotFound())
                .andExpect(handler -> {
                    final ExceptionInfo exceptionInfo = ResourceUtility.convertException(handler.getResponse().getContentAsString());
                    final Map<String, Object> info = exceptionInfo.getInfo();
                    assertThat(info).isNotNull();
                    assertThat(info.get(EntityNotFoundException.TYPE)).isEqualTo(Target.class.getSimpleName());
                    final List<String> notFound = (List<String>) info.get(EntityNotFoundException.ENTITY_ID);
                    Collections.sort(notFound);
                    assertThat(notFound).isEqualTo(missing);
                });
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent()).isEmpty();
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2) })
    public void assignTargetsNotFoundTagAndFail() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        mvc.perform(put(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param("onNotFoundPolicy", MgmtTargetTagRestApi.OnNotFoundPolicy.ON_WHAT_FOUND_AND_FAIL.name())
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isNotFound())
                .andExpect(handler -> {
                    final ExceptionInfo exceptionInfo = ResourceUtility.convertException(handler.getResponse().getContentAsString());
                    final Map<String, Object> info = exceptionInfo.getInfo();
                    assertThat(info).isNotNull();
                    assertThat(info.get(EntityNotFoundException.TYPE)).isEqualTo(Target.class.getSimpleName());
                    final List<String> notFound = (List<String>) info.get(EntityNotFoundException.ENTITY_ID);
                    Collections.sort(notFound);
                    assertThat(notFound).isEqualTo(missing);
                });
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent().stream().map(Target::getControllerId).sorted().toList())
                .isEqualTo(targets.stream().sorted().toList());
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2) })
    public void assignTargetsNotFoundTagAndSuccess() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        mvc.perform(put(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param("onNotFoundPolicy", MgmtTargetTagRestApi.OnNotFoundPolicy.ON_WHAT_FOUND_AND_SUCCESS.name())
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent().stream().map(Target::getControllerId).sorted().toList())
                .isEqualTo(targets.stream().sorted().toList());
    }

    @Test
    @Description("Verifies that tag unassignments done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 3) })
    public void unassignTarget() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<Target> targets = testdataFactory.createTargets(2);
        final Target assigned = targets.get(0);
        final Target unassigned = targets.get(1);

        targetManagement.assignTag(targets.stream().map(Target::getControllerId).collect(Collectors.toList()), tag.getId());

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned/" +
                        unassigned.getControllerId()))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        final List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();
        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsOnly(assigned.getControllerId());
    }

    @Test
    @Description("Verifies that tag unassignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 3),
            @Expect(type = TargetUpdatedEvent.class, count = 5) })
    public void unassignTargets() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<Target> targets = testdataFactory.createTargets(3);
        final Target assigned = targets.get(0);
        final Target unassigned0 = targets.get(1);
        final Target unassigned1 = targets.get(2);

        targetManagement.assignTag(targets.stream().map(Target::getControllerId).collect(Collectors.toList()), tag.getId());

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .content(JsonBuilder.toArray(Arrays.asList(unassigned0.getControllerId(), unassigned1.getControllerId())))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk());

        final List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();
        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsOnly(assigned.getControllerId());
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 2) })
    public void unassignTargetsNotFound() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        targetManagement.assignTag(targets, tag.getId());

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isNotFound())
                .andExpect(handler -> {
                    final ExceptionInfo exceptionInfo = ResourceUtility.convertException(handler.getResponse().getContentAsString());
                    final Map<String, Object> info = exceptionInfo.getInfo();
                    assertThat(info).isNotNull();
                    assertThat(info.get(EntityNotFoundException.TYPE)).isEqualTo(Target.class.getSimpleName());
                    final List<String> notFound = (List<String>) info.get(EntityNotFoundException.ENTITY_ID);
                    Collections.sort(notFound);
                    assertThat(notFound).isEqualTo(missing);
                });
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent().stream().map(Target::getControllerId).sorted().toList())
                .isEqualTo(targets.stream().sorted().toList());
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 4) })
    public void unassignTargetsNotFoundUntagAndFail() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        targetManagement.assignTag(targets, tag.getId());

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param("onNotFoundPolicy", MgmtTargetTagRestApi.OnNotFoundPolicy.ON_WHAT_FOUND_AND_FAIL.name())
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isNotFound())
                .andExpect(handler -> {
                    final ExceptionInfo exceptionInfo = ResourceUtility.convertException(handler.getResponse().getContentAsString());
                    final Map<String, Object> info = exceptionInfo.getInfo();
                    assertThat(info).isNotNull();
                    assertThat(info.get(EntityNotFoundException.TYPE)).isEqualTo(Target.class.getSimpleName());
                    final List<String> notFound = (List<String>) info.get(EntityNotFoundException.ENTITY_ID);
                    Collections.sort(notFound);
                    assertThat(notFound).isEqualTo(missing);
                });
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent()).isEmpty();
    }

    @Test
    @Description("Verifies that tag assignments (multi targets) done through tag API command are correctly stored in the repository.")
    @ExpectEvents({
            @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2),
            @Expect(type = TargetUpdatedEvent.class, count = 4) })
    public void unassignTargetsNotFoundUntagAndSuccess() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final List<String> targets = testdataFactory.createTargets(2).stream().map(Target::getControllerId).toList();
        final List<String> missing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            while (true) {
                final String id = String.valueOf(Math.abs(RND.nextLong()));
                if (!targets.contains(id)) {
                    missing.add(id);
                    break;
                }
            }
        }
        Collections.sort(missing);
        final List<String> withMissing = new ArrayList<>(targets);
        withMissing.addAll(missing);

        targetManagement.assignTag(targets, tag.getId());

        mvc.perform(delete(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .param("onNotFoundPolicy", MgmtTargetTagRestApi.OnNotFoundPolicy.ON_WHAT_FOUND_AND_SUCCESS.name())
                        .content(JsonBuilder.toArray(withMissing))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print())
                .andExpect(status().isOk());
        assertThat(targetManagement.findByTag(PAGE, tag.getId()).getContent()).isEmpty();
    }

    // DEPRECATED scenarios
    @Test
    @Description("Verifes that tag assignments done through toggle API command are correctly assigned or unassigned.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2), @Expect(type = TargetUpdatedEvent.class, count = 4) })
    public void toggleTagAssignment() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final int targetsAssigned = 2;
        final List<Target> targets = testdataFactory.createTargets(targetsAssigned);

        ResultActions result = toggle(tag, targets);

        List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();

        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsAll(targets.stream().map(Target::getControllerId).collect(Collectors.toList()));

        result.andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(0), "assignedTargets"))
                .andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(1), "assignedTargets"));

        result = toggle(tag, targets);

        updated = targetManagement.findAll(PAGE).getContent();

        result.andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(0), "unassignedTargets"))
                .andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(1), "unassignedTargets"));

        assertThat(targetManagement.findByTag(PAGE, tag.getId())).isEmpty();
    }

    @Test
    @Description("Verfies that tag assignments done through tag API command are correctly stored in the repository.")
    @ExpectEvents({ @Expect(type = TargetTagCreatedEvent.class, count = 1),
            @Expect(type = TargetCreatedEvent.class, count = 2), @Expect(type = TargetUpdatedEvent.class, count = 2) })
    public void assignTargetsByRequestBody() throws Exception {
        final TargetTag tag = testdataFactory.createTargetTags(1, "").get(0);
        final int targetsAssigned = 2;
        final List<Target> targets = testdataFactory.createTargets(targetsAssigned);

        final ResultActions result = mvc
                .perform(post(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId() + "/assigned")
                        .content(controllerIdsOld(
                                targets.stream().map(Target::getControllerId).collect(Collectors.toList())))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        final List<Target> updated = targetManagement.findByTag(PAGE, tag.getId()).getContent();

        assertThat(updated.stream().map(Target::getControllerId).collect(Collectors.toList()))
                .containsAll(targets.stream().map(Target::getControllerId).collect(Collectors.toList()));

        result.andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(0)))
                .andExpect(applyTargetEntityMatcherOnArrayResult(updated.get(1)));
    }

    private static String controllerIdsOld(final Collection<String> ids) throws JSONException {
        final JSONArray list = new JSONArray();
        for (final String smID : ids) {
            list.put(new JSONObject().put("controllerId", smID));
        }

        return list.toString();
    }

    private ResultActions toggle(final TargetTag tag, final List<Target> targets) throws Exception {
        return mvc
                .perform(post(MgmtRestConstants.TARGET_TAG_V1_REQUEST_MAPPING + "/" + tag.getId()
                        + "/assigned/toggleTagAssignment")
                        .content(controllerIdsOld(
                                targets.stream().map(Target::getControllerId).collect(Collectors.toList())))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andDo(MockMvcResultPrinter.print()).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    }
}