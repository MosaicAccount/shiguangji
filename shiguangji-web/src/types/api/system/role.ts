import type { PageDomain, BaseEntity } from "../common";

/** 角色分页查询参数 */
export interface RoleQueryParams extends PageDomain {
  /** 角色名称 */
  roleName?: string;
  /** 角色权限 */
  roleKey?: string;
  /** 状态 */
  status?: string;
  /** 创建时间 */
  params?: {
    beginTime?: string;
    endTime?: string;
  };
}

/** 批量授权用户参数 */
export interface AuthUserSelectParams {
  roleId: number | string;
  /** 用户编号组（逗号分隔字符串） */
  userIds: number[] | string;
}

/** 用户和角色关联信息 */
export interface SysUserRole {
  /** 用户编号 */
  userId?: number;
  /** 角色编号（路由参数场景可能为字符串） */
  roleId: number | string;
}

/** 用户和多角色关联信息 */
export interface SysUserRoles {
  /** 用户编号 */
  userId?: number;
  /** 角色编号组（逗号分隔字符串） */
  roleIds?: number[] | string;
}


/** 角色信息 */
export interface SysRole extends BaseEntity {
  /** 角色编号 */
  roleId?: number;
  /** 角色名称 */
  roleName?: string;
  /** 角色权限 */
  roleKey?: string;
  /** 角色排序  */
  roleSort?: number;
  /** 菜单树选择项是否关联显示 */
  menuCheckStrictly?: boolean;
  /** 角色权限 */
  menuIds?: number[];
  /** 状态（0正常 1停用） */
  status?: '0' | '1';
  /** 用户是否存在此角色标识（授权查询时由后端返回） */
  flag?: boolean;
}
