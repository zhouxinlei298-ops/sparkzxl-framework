CREATE
    DEFINER = `root`@`%` FUNCTION `find_in_set_multiple`(str_list VARCHAR(255), search_str VARCHAR(255)) RETURNS int(11)
    DETERMINISTIC
BEGIN
    DECLARE pos INT DEFAULT 1; -- 当前查找的起始位置
    DECLARE result INT DEFAULT 0; -- 默认返回的匹配结果false
    DECLARE current_str VARCHAR(255); -- 当前字符串
    DECLARE comma_pos INT;
    -- 逗号的位置

    -- 检查str_list是否为空
    IF str_list IS NULL OR TRIM(str_list) = '' THEN
        RETURN 0;
    END IF;

    WHILE pos > 0
        DO
            SET comma_pos = INSTR(str_list, ','); -- 查找逗号的位置

            IF comma_pos = 0 THEN -- 如果没有逗号，那么字符串就是最后的字符串
                SET current_str = str_list;
                SET pos = 0;
            ELSE
                SET current_str = SUBSTRING(str_list, 1, comma_pos - 1); -- 截取逗号之前的字符串
                SET str_list = SUBSTRING(str_list, comma_pos + 1);
            END IF;

            -- 如果当前字符串存在于search_str中（这里假设search_str是一个逗号分隔的列表）
            -- 使用LIKE操作符进行模糊匹配（注意这可能会有性能问题）
            IF FIND_IN_SET(current_str, search_str) > 0 THEN
                SET result = 1;
                SET pos = 0; -- 找到匹配项，退出循环
            END IF;
        END WHILE;

    RETURN result;
END
