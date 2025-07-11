--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

-- CREATE TABLE migration_plans
-- (
--     id character(36) NOT NULL,
--     migration_plan JSONB NOT NULL,

--     CONSTRAINT migration_plans_pk
--     PRIMARY KEY id
-- );

-- CREATE TABLE migration_plans_process_instances
-- (
--     migration_plan_id character(36) NOT NULL,
--     process_instance_id character(36) NOT NULL,
--     migrated BOOLEAN NOT NULL,

--     PRIMARY KEY (migration_plan_id, process_instance_id),
--     CONSTRAINT migration_plans_fk
--         FOREIGN KEY (migration_plan_id)
--         REFERENCES  migration_plans(id)
--         ON DELETE CASCADE,

--     CONSTRAINT process_instances_fk
--         FOREIGN KEY (process_instance_id)
--         REFERENCES  process_instances(id)
-- );