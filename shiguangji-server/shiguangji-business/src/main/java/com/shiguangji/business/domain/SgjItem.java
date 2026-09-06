package com.shiguangji.business.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 内容条目主表 sgj_item
 * 同时承载电影/电视剧/书籍/地点的扩展字段，由 service 层拆分到扩展表
 *
 * @author shiguangji
 */
public class SgjItem extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 条目ID */
    private Long itemId;

    /** 条目类型（MOVIE/TV/BOOK/PLACE） */
    private String itemType;

    /** 标题/名称 */
    private String title;

    /** 状态（WANT/DONE） */
    private String status;

    /** 评分 */
    private BigDecimal rating;

    /** 个人短评 */
    private String comment;

    /** 标签 */
    private String tags;

    /** 封面图/图片地址 */
    private String coverUrl;

    /** 开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date finishDate;

    /** 电影/电视剧：导演 */
    private String director;

    /** 电影/电视剧：主演 */
    private String actors;

    /** 类型/题材 */
    private String genre;

    /** 地区 */
    private String region;

    /** 语言 */
    private String language;

    /** 电影：上映年份 */
    private Integer releaseYear;

    /** 电影：片长（分钟） */
    private Integer durationMinutes;

    /** IMDb编号 */
    private String imdbId;

    /** 豆瓣编号 */
    private String doubanId;

    /** 电视剧：开播年份 */
    private Integer startYear;

    /** 电视剧：完结年份 */
    private Integer endYear;

    /** 电视剧：季数 */
    private Integer seasonCount;

    /** 电视剧：总集数 */
    private Integer episodeCount;

    /** 书籍：作者 */
    private String author;

    /** 书籍：出版社 */
    private String publisher;

    /** 书籍：出版日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date publishDate;

    /** 书籍：ISBN */
    private String isbn;

    /** 书籍：页数 */
    private Integer pages;

    /** 地点：详细地址 */
    private String address;

    /** 地点：城市 */
    private String city;

    /** 地点：省/州 */
    private String province;

    /** 地点：国家 */
    private String country;

    /** 地点：纬度 */
    private BigDecimal latitude;

    /** 地点：经度 */
    private BigDecimal longitude;

    /** 地点：最佳季节 */
    private String bestSeason;

    /** 地点：地点分类 */
    private String placeCategory;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public String getItemType()
    {
        return itemType;
    }

    public void setItemType(String itemType)
    {
        this.itemType = itemType;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public BigDecimal getRating()
    {
        return rating;
    }

    public void setRating(BigDecimal rating)
    {
        this.rating = rating;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public Date getStartDate()
    {
        return startDate;
    }

    public void setStartDate(Date startDate)
    {
        this.startDate = startDate;
    }

    public Date getFinishDate()
    {
        return finishDate;
    }

    public void setFinishDate(Date finishDate)
    {
        this.finishDate = finishDate;
    }

    public String getDirector()
    {
        return director;
    }

    public void setDirector(String director)
    {
        this.director = director;
    }

    public String getActors()
    {
        return actors;
    }

    public void setActors(String actors)
    {
        this.actors = actors;
    }

    public String getGenre()
    {
        return genre;
    }

    public void setGenre(String genre)
    {
        this.genre = genre;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public String getLanguage()
    {
        return language;
    }

    public void setLanguage(String language)
    {
        this.language = language;
    }

    public Integer getReleaseYear()
    {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear)
    {
        this.releaseYear = releaseYear;
    }

    public Integer getDurationMinutes()
    {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes)
    {
        this.durationMinutes = durationMinutes;
    }

    public String getImdbId()
    {
        return imdbId;
    }

    public void setImdbId(String imdbId)
    {
        this.imdbId = imdbId;
    }

    public String getDoubanId()
    {
        return doubanId;
    }

    public void setDoubanId(String doubanId)
    {
        this.doubanId = doubanId;
    }

    public Integer getStartYear()
    {
        return startYear;
    }

    public void setStartYear(Integer startYear)
    {
        this.startYear = startYear;
    }

    public Integer getEndYear()
    {
        return endYear;
    }

    public void setEndYear(Integer endYear)
    {
        this.endYear = endYear;
    }

    public Integer getSeasonCount()
    {
        return seasonCount;
    }

    public void setSeasonCount(Integer seasonCount)
    {
        this.seasonCount = seasonCount;
    }

    public Integer getEpisodeCount()
    {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount)
    {
        this.episodeCount = episodeCount;
    }

    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public String getPublisher()
    {
        return publisher;
    }

    public void setPublisher(String publisher)
    {
        this.publisher = publisher;
    }

    public Date getPublishDate()
    {
        return publishDate;
    }

    public void setPublishDate(Date publishDate)
    {
        this.publishDate = publishDate;
    }

    public String getIsbn()
    {
        return isbn;
    }

    public void setIsbn(String isbn)
    {
        this.isbn = isbn;
    }

    public Integer getPages()
    {
        return pages;
    }

    public void setPages(Integer pages)
    {
        this.pages = pages;
    }

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getProvince()
    {
        return province;
    }

    public void setProvince(String province)
    {
        this.province = province;
    }

    public String getCountry()
    {
        return country;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public BigDecimal getLatitude()
    {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude)
    {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude()
    {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude)
    {
        this.longitude = longitude;
    }

    public String getBestSeason()
    {
        return bestSeason;
    }

    public void setBestSeason(String bestSeason)
    {
        this.bestSeason = bestSeason;
    }

    public String getPlaceCategory()
    {
        return placeCategory;
    }

    public void setPlaceCategory(String placeCategory)
    {
        this.placeCategory = placeCategory;
    }
}