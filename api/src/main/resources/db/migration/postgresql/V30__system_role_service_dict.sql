-- 系统角色：服务账号（Service），用于系统/后台程序访问。
INSERT INTO sys_dict
VALUES (nextval('sys_dict_id_seq')::text, 'System_Role_Service', 'System_Role', '服务账号', '服务账号', '', NULL, 1,
        FALSE, 1783760000000, 1783760000000, 3);