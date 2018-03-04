package com.nian.dao;

import org.springframework.stereotype.Repository;
import com.nian.pojo.UserRedPacket;

@Repository
public interface UserRedPacketDao {

	/**
	 * 插入抢红包信息.
	 * @param userRedPacket 抢红包信息
	 * @return 影响记录数.
	 */
	public int grapRedPacket(UserRedPacket  userRedPacket);
}
