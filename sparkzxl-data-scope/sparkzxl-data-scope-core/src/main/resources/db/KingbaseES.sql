CREATE OR REPLACE FUNCTION "apollo_wenzhou_dev"."find_in_set_multiple"("str_list" varchar, "search_strs" varchar)
              RETURNS "pg_catalog"."int4" AS $BODY$
              DECLARE
              result INT := 0;
current_str VARCHAR(255);
search_str_array TEXT[];
str_list_array TEXT[];
i INT;
BEGIN
    -- 去除str_list两端的空格
    str_list := TRIM(str_list);

-- 如果str_list为空，则直接返回0
IF str_list = '' THEN
        RETURN 0;
END IF;

    -- 将search_strs和str_list按逗号分割成数组
search_str_array := STRING_TO_ARRAY(search_strs, ',');
str_list_array := STRING_TO_ARRAY(str_list, ',');

    -- 遍历str_list数组中的每个元素
FOR i IN 1..ARRAY_LENGTH(str_list_array, 1) LOOP
        current_str := str_list_array[i];

        -- 检查当前元素是否在search_strs数组中
IF current_str = ANY(search_str_array) THEN
            result := 1; -- 找到匹配项，设置result为1
EXIT; -- 退出循环
END IF;
END LOOP;

RETURN result;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100
