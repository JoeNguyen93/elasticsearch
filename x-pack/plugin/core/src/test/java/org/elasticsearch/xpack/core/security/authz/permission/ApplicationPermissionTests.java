/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.authz.permission;

import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.security.authz.privilege.ApplicationPrivilege;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;

public class ApplicationPermissionTests extends ESTestCase {

    private ApplicationPrivilege app1All = new ApplicationPrivilege("app1", "all", "*");
    private ApplicationPrivilege app1Read = new ApplicationPrivilege("app1", "read", "read/*");
    private ApplicationPrivilege app1Write = new ApplicationPrivilege("app1", "write", "write/*");
    private ApplicationPrivilege app1Delete = new ApplicationPrivilege("app1", "delete", "write/delete");
    private ApplicationPrivilege app1Create = new ApplicationPrivilege("app1", "create", "write/create");
    private ApplicationPrivilege app2Read = new ApplicationPrivilege("app2", "read", "read/*");

    private List<ApplicationPrivilege> all = Arrays.asList(app1All, app1Read, app1Write, app1Create, app1Delete, app2Read);

    public void testCheckSimplePermission() {
        final ApplicationPermission hasPermission = buildPermission(app1Write, "*");
        assertThat(hasPermission.grants(app1Write, "*"), equalTo(true));
        assertThat(hasPermission.grants(app1Write, "foo"), equalTo(true));
        assertThat(hasPermission.grants(app1Delete, "*"), equalTo(true));
        assertThat(hasPermission.grants(app1Create, "foo"), equalTo(true));

        assertThat(hasPermission.grants(app1Read, "*"), equalTo(false));
        assertThat(hasPermission.grants(app1Read, "foo"), equalTo(false));
        assertThat(hasPermission.grants(app1All, "*"), equalTo(false));
        assertThat(hasPermission.grants(app1All, "foo"), equalTo(false));
    }

    public void testResourceMatching() {
        final ApplicationPermission hasPermission = buildPermission(app1All, "dashboard/*", "audit/*", "user/12345");

        assertThat(hasPermission.grants(app1Write, "*"), equalTo(false));
        assertThat(hasPermission.grants(app1Write, "dashboard"), equalTo(false));
        assertThat(hasPermission.grants(app1Write, "dashboard/999"), equalTo(true));

        assertThat(hasPermission.grants(app1Create, "audit/2018-02-21"), equalTo(true));
        assertThat(hasPermission.grants(app1Create, "report/2018-02-21"), equalTo(false));

        assertThat(hasPermission.grants(app1Read, "user/12345"), equalTo(true));
        assertThat(hasPermission.grants(app1Read, "user/67890"), equalTo(false));

        assertThat(hasPermission.grants(app1All, "dashboard/999"), equalTo(true));
        assertThat(hasPermission.grants(app1All, "audit/2018-02-21"), equalTo(true));
        assertThat(hasPermission.grants(app1All, "user/12345"), equalTo(true));
    }

    public void testActionMatching() {
        final ApplicationPermission hasPermission = buildPermission(app1Write, "allow/*");

        final ApplicationPrivilege update = actionPrivilege("app1", "write/update");
        assertThat(hasPermission.grants(update, "allow/1"), equalTo(true));
        assertThat(hasPermission.grants(update, "deny/1"), equalTo(false));

        final ApplicationPrivilege updateCreate = actionPrivilege("app1", "write/update", "write/create");
        assertThat(hasPermission.grants(updateCreate, "allow/1"), equalTo(true));
        assertThat(hasPermission.grants(updateCreate, "deny/1"), equalTo(false));

        final ApplicationPrivilege manage = actionPrivilege("app1", "admin/manage");
        assertThat(hasPermission.grants(manage, "allow/1"), equalTo(false));
        assertThat(hasPermission.grants(manage, "deny/1"), equalTo(false));
    }

    public void testDoesNotMatchAcrossApplications() {
        assertThat(buildPermission(app1Read, "*").grants(app1Read, "123"), equalTo(true));
        assertThat(buildPermission(app1All, "*").grants(app1Read, "123"), equalTo(true));

        assertThat(buildPermission(app1Read, "*").grants(app2Read, "123"), equalTo(false));
        assertThat(buildPermission(app1All, "*").grants(app2Read, "123"), equalTo(false));
    }

    public void testMergedPermissionChecking() {
        final ApplicationPrivilege app1ReadWrite = ApplicationPrivilege.get("app1", Sets.union(app1Read.name(), app1Write.name()), all);
        final ApplicationPermission hasPermission = buildPermission(app1ReadWrite, "allow/*");

        assertThat(hasPermission.grants(app1Read, "allow/1"), equalTo(true));
        assertThat(hasPermission.grants(app1Write, "allow/1"), equalTo(true));

        assertThat(hasPermission.grants(app1Read, "deny/1"), equalTo(false));
        assertThat(hasPermission.grants(app1Write, "deny/1"), equalTo(false));

        assertThat(hasPermission.grants(app1All, "allow/1"), equalTo(false));
        assertThat(hasPermission.grants(app2Read, "allow/1"), equalTo(false));
    }

    private ApplicationPrivilege actionPrivilege(String appName, String... actions) {
        return ApplicationPrivilege.get(appName, Sets.newHashSet(actions), Collections.emptyList());
    }

    private ApplicationPermission buildPermission(ApplicationPrivilege privilege, String... resources) {
        return new ApplicationPermission(singletonList(new Tuple<>(privilege, Sets.newHashSet(resources))));
    }
}
