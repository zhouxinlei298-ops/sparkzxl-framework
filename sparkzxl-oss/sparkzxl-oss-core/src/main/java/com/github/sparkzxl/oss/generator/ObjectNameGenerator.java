package com.github.sparkzxl.oss.generator;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.core.util.IdUtil;
import com.github.sparkzxl.core.util.DateUtils;

import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

/**
 * <p>
 * objectName生成器
 * </p>
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-05-13 15:48
 */
public interface ObjectNameGenerator {

    String SPLIT_STR = "/";

    ObjectNameGenerator DEFAULT_OBJECT_NAME_GENERATOR =  new ObjectNameGenerator(){
        @Override
        public String generator(String environment, String tenantId, String originalFilename) {
            return environment.concat(SPLIT_STR)
                    .concat(tenantId)
                    .concat(SPLIT_STR)
                    .concat(DateUtils.now(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                    .concat(StrPool.SLASH)
                    .concat(fileNameGenerator().generator(originalFilename));
        }

        @Override
        public OssFileNameGenerator fileNameGenerator() {
            return OssFileNameGenerator.DEFAULT_FILE_NAME_GENERATOR;
        }
    };


    String generator(String environment, String tenantId, String originalFilename);

    OssFileNameGenerator fileNameGenerator();

    /**
     * <p>
     * oss文件名生成
     * </p>
     *
     * @author zhouxinlei
     * @version 1.0
     * @since 2026-05-13 16:03
     */
    interface OssFileNameGenerator {

        OssFileNameGenerator DEFAULT_FILE_NAME_GENERATOR = new OssFileNameGenerator() {
            @Override
            public String generator(String originalFilename) {
                String suffix = FileUtil.extName(originalFilename);
                String uniqueFileName = IdUtil.fastSimpleUUID();
                return new StringJoiner(StrPool.DOT)
                        .add(uniqueFileName)
                        .add(suffix)
                        .toString();
            }
        };

        String generator(String originalFilename);
    }
}
