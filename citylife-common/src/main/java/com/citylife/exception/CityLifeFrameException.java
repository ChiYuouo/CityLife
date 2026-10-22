package com.citylife.exception;


import lombok.Data;
import lombok.EqualsAndHashCode;
import com.citylife.enums.BaseCode;

/**
 * @description: 业务异常
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class CityLifeFrameException extends BaseException {
	
	private Integer code;
	
	private String message;

	public CityLifeFrameException() {
		super();
	}

	public CityLifeFrameException(String message) {
		super(message);
	}
	
	public CityLifeFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public CityLifeFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public CityLifeFrameException(Throwable cause) {
		super(cause);
	}

	public CityLifeFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}
}
