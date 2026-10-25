#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.interfaces;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.sparkzxl.log.annotation.HttpRequestLog;
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ${package}.system.application.model.UserQueryDTO;
import ${package}.system.application.model.UserSaveDTO;
import ${package}.system.application.model.UserUpdateDTO;
import ${package}.system.application.model.UserVO;
import ${package}.system.application.service.UserService;
import ${package}.system.application.service.query.UserQueryService;

import java.util.List;

/**
 * <p>
 * 系统用户 前端控制器
 * <p>interfaces 层只放 Controller，入参出参模型一律在 application/model 下。</p>
 *
 * @author zhouxinlei
 */
@Slf4j
@ResponseResult
@RestController
@RequestMapping("/user")
@Api(tags = {"系统用户管理"})
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final UserQueryService userQueryService;

    @ApiOperation(value = "系统用户分页查询", httpMethod = "POST", response = Page.class)
    @ApiImplicitParams({
        @ApiImplicitParam(name = "current", value = "当前页", dataType = "int", example = "1"),
        @ApiImplicitParam(name = "size", value = "分页显示数量", dataType = "int", example = "10"),
    })
    @HttpRequestLog(value = "系统用户分页查询")
    @PostMapping("/page")
    public Page<UserVO> userPage(@RequestParam(value = "current") Integer current,
                                 @RequestParam(value = "size") Integer size,
                                 @RequestBody UserQueryDTO userQueryDTO) {
        return userQueryService.page(new Page<>(current, size), userQueryDTO);
    }

    @ApiOperation(value = "系统用户列表查询", httpMethod = "POST", response = UserVO.class)
    @HttpRequestLog(value = "系统用户列表查询")
    @PostMapping("/list")
    public List<UserVO> userList(@RequestBody UserQueryDTO userQueryDTO) {
        return userQueryService.list(userQueryDTO);
    }

    @ApiOperation(value = "系统用户详情查询", httpMethod = "GET", response = UserVO.class)
    @HttpRequestLog(value = "系统用户详情查询")
    @GetMapping("/getById")
    public UserVO getUserById(@RequestParam(value = "id") Long id) {
        return userQueryService.getById(id);
    }

    @ApiOperation(value = "系统用户新增", httpMethod = "POST", response = Boolean.class)
    @HttpRequestLog(value = "系统用户新增")
    @PostMapping("/save")
    public Boolean saveUser(@Validated @RequestBody UserSaveDTO userSaveDTO) {
        return userService.save(userSaveDTO);
    }

    @ApiOperation(value = "系统用户修改", httpMethod = "POST", response = Boolean.class)
    @HttpRequestLog(value = "系统用户修改")
    @PostMapping("/update")
    public Boolean updateUser(@Validated @RequestBody UserUpdateDTO userUpdateDTO) {
        return userService.update(userUpdateDTO);
    }

    @ApiOperation(value = "系统用户删除", httpMethod = "DELETE", response = Boolean.class)
    @HttpRequestLog(value = "系统用户删除")
    @DeleteMapping("/delete")
    public Boolean deleteById(@RequestParam(value = "id") Long id) {
        return userService.deleteById(id);
    }

}
