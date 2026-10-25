package ${customizeConfig.insideServiceImplConfig.packageName};

import ${package.Entity}.${entity};
import ${package.Service}.${table.serviceName};
import ${customizeConfig.insideServiceConfig.packageName}.${entity}Service;
import ${customizeConfig.convertConfig.packageName}.${entity}Convert;
import ${customizeConfig.voConfig.packageName}.${entity}VO;
import ${customizeConfig.saveDtoConfig.packageName}.${entity}SaveDTO;
import ${customizeConfig.updateDtoConfig.packageName}.${entity}UpdateDTO;
import ${customizeConfig.dtoConfig.packageName}.${entity}DTO;
import org.apache.commons.lang3.ObjectUtils;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * <p>
 * ${table.comment!} 服务实现类
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@Service
<#if kotlin>
open class ${table.serviceImplName}ServiceImpl : ${entity}Service {

}
<#else>
@RequiredArgsConstructor
public class ${entity}ServiceImpl implements ${entity}Service {

    private final ${table.serviceName} ${table.serviceName?uncap_first};

    /**
    * 分页条件查询
    * @author ${author}
    * @since ${date}
    * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
    * @param page: 分页入参
    * @return Page<${entity}VO>
    */
    @Override
    public Page<${entity}VO> page(Page<${entity}> page, ${entity}DTO ${entity?uncap_first}Dto) {
        queryPageCheck(${entity?uncap_first}Dto);
        ${entity} ${entity?uncap_first} = convertPageQuery${entity}(${entity?uncap_first}Dto);
        QueryWrapper<${entity}> queryWrapper = new QueryWrapper<>(${entity?uncap_first});
        buildPageQueryWrapper(${entity?uncap_first}, queryWrapper);
        Page<${entity}> ${entity?uncap_first}Page = ${table.serviceName?uncap_first}.page(page, queryWrapper);
        return assignment(convert${entity}VoPage(${entity?uncap_first}Page));
    }

    /**
     * 分页条件检查
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
     */
    private void queryPageCheck(${entity}DTO ${entity?uncap_first}Dto) {

    }

    /**
     * 分页查询模型转换
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
     * @return ${entity}
     */
     private ${entity} convertPageQuery${entity}(${entity}DTO ${entity?uncap_first}Dto){
        return ${entity}Convert.INSTANCE.convert${entity}(${entity?uncap_first}Dto);
    }

    /**
     * 构建分页查询条件
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}: ${table.comment!}
     * @param queryWrapper: 查询Wrapper
     */
    private void buildPageQueryWrapper(${entity} ${entity?uncap_first}, QueryWrapper<${entity}> queryWrapper) {

    }

    /**
     * 分页,增强返回参数追加
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}VOPage: 分页显示VO
     * @return Page<${entity}>
     */
    private Page<${entity}VO> assignment(Page<${entity}VO> ${entity?uncap_first}VOPage) {
        ${entity?uncap_first}VOPage.getRecords().forEach(${entity?uncap_first}VO -> {
        });
        return ${entity?uncap_first}VOPage;
    }

    /**
     * 分页显示模型转换
     *
     * @author ${author}
     * @since ${date}
     * @param page: 分页显示VO
     * @return Page<${entity}>
     */
     private Page<${entity}VO> convert${entity}VoPage(Page<${entity}> page){
        return ${entity}Convert.INSTANCE.convert${entity}VoPage(page);
    }

    /**
     * 集合条件查询
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
     * @return List<${entity}VO>
     */
    @Override
    public List<${entity}VO> list(${entity}DTO ${entity?uncap_first}Dto) {
        queryListCheck(${entity?uncap_first}Dto);
        ${entity} ${entity?uncap_first} = convert${entity}ListQuery(${entity?uncap_first}Dto);
        QueryWrapper<${entity}> queryWrapper = new QueryWrapper<>(${entity?uncap_first});
        buildListQueryWrapper(${entity?uncap_first}, queryWrapper);
        List<${entity}> ${entity?uncap_first}List = ${table.serviceName?uncap_first}.list(queryWrapper);
        List<${entity}VO> ${entity?uncap_first}VoList = convert${entity}VoList(${entity?uncap_first}List);
        return assignment(${entity?uncap_first}VoList);
    }

    /**
     * 集合条件检查
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
     */
     private void queryListCheck(${entity}DTO ${entity?uncap_first}Dto) {

    }

    /**
    * 列表查询模型转换
    *
    * @author ${author}
    * @since ${date}
    * @param ${entity?uncap_first}Dto: ${table.comment!}DTO对象
    * @return Page<${entity}>
    */
    private ${entity} convert${entity}ListQuery(${entity}DTO ${entity?uncap_first}Dto){
        return ${entity}Convert.INSTANCE.convert${entity}(${entity?uncap_first}Dto);
    }

    /**
    * 构建列表查询条件
    *
    * @author ${author}
    * @since ${date}
    * @param ${entity?uncap_first}: ${table.comment!}
    * @param queryWrapper: 查询Wrapper
    */
    private void buildListQueryWrapper(${entity} ${entity?uncap_first}, QueryWrapper<${entity}> queryWrapper) {

    }

    /**
     * 列表显示模型转换
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}List: ${table.comment!}列表
     * @return List<${entity}VO>
     */
     public List<${entity}VO> convert${entity}VoList(List<${entity}> ${entity?uncap_first}List){
         return ${entity}Convert.INSTANCE.convert${entity}VoList(${entity?uncap_first}List);
     }

     /**
      * 集合,增强返回参数追加
      *
      * @author ${author}
      * @since ${date}
      * @param ${entity?uncap_first}VoList: ${table.comment!}VO列表
      * @return List<${entity}>
      */
     private List<${entity}VO> assignment(List<${entity}VO> ${entity?uncap_first}VoList) {
        ${entity?uncap_first}VoList.forEach(${entity?uncap_first}VO -> {
        });
        return  ${entity?uncap_first}VoList;
     }

    /**
     * 单条条件查询
     * @author ${author}
     * @since ${date}
     * @param id: id
     * @return ${entity}VO
     */
    @Override
    public ${entity}VO getById(Long id) {
        ${entity} ${entity?uncap_first} = ${table.serviceName?uncap_first}.getById(id);
        ${entity}VO ${entity?uncap_first}Vo = convert${entity}Vo(${entity?uncap_first});
        return assignment(${entity?uncap_first}Vo);
    }

    /**
     * 详情显示模型转换
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}: ${table.comment!}
     * @return ${entity}VO
     */
     private ${entity}VO convert${entity}Vo(${entity} ${entity?uncap_first}){
        return ${entity}Convert.INSTANCE.convert${entity}Vo(${entity?uncap_first});
    }

    /**
     * 单条，增强返回参数追加
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}VO: ${table.comment!}VO
     * @return ${entity}
     */
    private ${entity}VO assignment(${entity}VO ${entity?uncap_first}VO) {
        return ${entity?uncap_first}VO;
    }

    /**
     * 新增${table.comment!}
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}SaveDto ${table.comment!}新增对象
     * @return boolean
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 180, rollbackFor = Exception.class)
    public boolean save(${entity}SaveDTO ${entity?uncap_first}SaveDto) {
        saveCheck(${entity?uncap_first}SaveDto);
        ${entity} ${entity?uncap_first} = convert${entity}SaveModel(${entity?uncap_first}SaveDto);
        return ${table.serviceName?uncap_first}.save(${entity?uncap_first});
    }

    /**
     * 保存检查
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}SaveDto: ${table.comment!}保存Dto
     */
    private void saveCheck(${entity}SaveDTO ${entity?uncap_first}SaveDto) {

    }

    /**
     * 新增模型转换
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}SaveDto: ${table.comment!}保存Dto
     * @return ${entity}
     */
     private ${entity} convert${entity}SaveModel(${entity}SaveDTO ${entity?uncap_first}SaveDto){
        return ${entity}Convert.INSTANCE.convert${entity}(${entity?uncap_first}SaveDto);
    }

    /**
     * 修改${table.comment!}
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}UpdateDto ${table.comment!}修改Dto
     * @return boolean
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 180, rollbackFor = Exception.class)
    public boolean updateById(${entity}UpdateDTO ${entity?uncap_first}UpdateDto) {
        updateCheck(${entity?uncap_first}UpdateDto);
        ${entity} ${entity?uncap_first} = convertUpdate${entity}Model(${entity?uncap_first}UpdateDto);
        LambdaUpdateWrapper<${entity}> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        buildUpdateWrapper(${entity?uncap_first},lambdaUpdateWrapper);
        return ${table.serviceName?uncap_first}.update(${entity?uncap_first},lambdaUpdateWrapper);
    }

    /**
     * 更新检查
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}UpdateDto: ${table.comment!}更新对象
     */
     private void updateCheck(${entity}UpdateDTO ${entity?uncap_first}UpdateDto) {

    }

    /**
     * 构建修改条件
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}: ${table.comment!}
     * @param updateWrapper: 更新Wrapper
     */
    private void buildUpdateWrapper(${entity} ${entity?uncap_first}, LambdaUpdateWrapper<${entity}> updateWrapper) {
        // 更新条件以及设值
        updateWrapper.eq(${entity}::getId,${entity?uncap_first}.getId());
    }

    /**
     * 修改模型转换
     *
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}UpdateDto: ${table.comment!}更新对象
     * @return ${entity}
     */
    public ${entity} convertUpdate${entity}Model(${entity}UpdateDTO ${entity?uncap_first}UpdateDto){
        ArgumentAssert.notNull(${entity?uncap_first}UpdateDto.getId(),"ID不能为空");
        return ${entity}Convert.INSTANCE.convert${entity}(${entity?uncap_first}UpdateDto);
    }

    /**
     * 删除
     * @author ${author}
     * @since ${date}
     * @param id: id
     * @return boolean
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, timeout = 180, rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        return ${table.serviceName?uncap_first}.removeById(id);
    }

    /**
     * Excel导入
     * @author ${author}
     * @since ${date}
     * @param multipartFile: 文件
     * @return Integer
     */
    @Override
    public Integer importExcel(MultipartFile multipartFile){
        return 0;
    }

    /**
     * Excel导出
     * @author ${author}
     * @since ${date}
     * @param ${entity?uncap_first}Dto ${table.comment!}导出DTO
     * @param response http response
     */
     @Override
     public void exportData(${entity}DTO ${entity?uncap_first}Dto, HttpServletResponse response){

    }
}

</#if>
