package com.citylife.exception;

import lombok.Data;

/**
 * @description: 参数错误
 **/
@Data
public class ArgumentError {
	
	private String argumentName;
	
	private String message;
}
