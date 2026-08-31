package com.yeni.backoffice.api.commerce.service;
import com.yeni.backoffice.core.common.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
@Service
public class ProductImageStorageService {
    private static final long MAX_SIZE=5L*1024*1024;
    private final Path root;
    public ProductImageStorageService(@Value("${commerce.product-image-dir:./data/uploads/products}") String path){root=Paths.get(path).toAbsolutePath().normalize();}
    public String store(MultipartFile file){
        if(file==null||file.isEmpty())throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"이미지 파일을 선택해 주세요.");
        if(file.getSize()>MAX_SIZE)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"이미지는 5MB 이하만 등록할 수 있습니다.");
        String extension=extension(file.getOriginalFilename()),content=Objects.toString(file.getContentType(),"").toLowerCase();
        if(!Set.of("jpg","jpeg","png","webp","gif").contains(extension)||!content.startsWith("image/"))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"JPG, PNG, WEBP, GIF 이미지만 등록할 수 있습니다.");
        String filename=UUID.randomUUID()+"."+extension;Path target=root.resolve(filename).normalize();
        if(!target.startsWith(root))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"올바르지 않은 저장 경로입니다.");
        try{byte[] bytes=file.getBytes();if(!hasImageSignature(bytes,extension))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"이미지 파일 내용이 올바르지 않습니다.");Files.createDirectories(root);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);}catch(ValidationBusinessException e){throw e;}catch(IOException e){throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,"이미지 저장에 실패했습니다.");}
        return "/uploads/products/"+filename;
    }
    public Path root(){return root;}
    private String extension(String name){if(name==null)return "";int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase();}
    private boolean hasImageSignature(byte[] b,String ext){if(b.length<4)return false;if(ext.equals("png"))return b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;if(ext.equals("jpg")||ext.equals("jpeg"))return(b[0]&255)==255&&(b[1]&255)==216;if(ext.equals("gif"))return b[0]=='G'&&b[1]=='I'&&b[2]=='F';if(ext.equals("webp"))return b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';return false;}
}
