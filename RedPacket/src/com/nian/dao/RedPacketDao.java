package com.nian.dao;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import com.nian.pojo.RedPacket;

@Repository
public interface RedPacketDao {
	
	/**
	 * 获取红包信息.
	 * @param id 红包id
	 * @return 红包具体信息
	 */
	public RedPacket getRedPacket(Long id);
	
	/**
	 * 扣减抢红包数.
	 * @param id 红包id
	 * @return 更新记录条数
	 */
	public int decreaseRedPacket(Long id);
	
	/**
	 * 使用for update语句加锁.
	 * @param id 红包id
	 * @return 红包信息
	 */
	public RedPacket getRedPacketForUpdate(Long id);
	/**
	 * 多个参数用Param注解
	 * @param id
	 * @param version 版本号
	 * @return
	 */
	public int decreaseRedPacketForVersion(@Param("id") Long id, @Param("version") Integer version);
}