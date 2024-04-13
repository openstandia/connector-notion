/*
 *  Copyright Nomura Research Institute, Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.openstandia.connector.notion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotionGroupModel {
    private static final String GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";

    public List<String> schemas = Collections.singletonList(GROUP);
    public String id; // auto generated
    public String displayName;
    public List<Member> members;
    public Meta meta;

    public void addMembers(List<String> source) {
        if (members == null) {
            members = new ArrayList<>();
        }
        for (String s : source) {
            Member member = new Member();
            member.value = s;
            members.add(member);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Member {
        public String value;
        @JsonProperty("$ref")
        public String ref;
        public String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        public String resourceType;
        public String created;
        public String lastModified;
        public String location;
    }
}
