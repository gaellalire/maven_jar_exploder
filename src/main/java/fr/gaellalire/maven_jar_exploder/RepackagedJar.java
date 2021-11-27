package fr.gaellalire.maven_jar_exploder;

import java.util.List;

public class RepackagedJar {
    
    private String positions;
    
    private String times;
    
    private String externalAttributesList;
    
    private List<String> excludedURL;

    private List<MetaAndSha512> filesToAdd;

    private String url;

    public RepackagedJar(String positions, String times, String externalAttributesList, List<String> excludedURL, String url, List<MetaAndSha512> filesToAdd) {
        this.positions = positions;
        this.times = times;
        this.externalAttributesList = externalAttributesList;
        this.excludedURL = excludedURL;
        this.url = url;
        this.filesToAdd = filesToAdd;
    }

    public String getPositions() {
        return positions;
    }

    public String getTimes() {
        return times;
    }

    public String getExternalAttributesList() {
        return externalAttributesList;
    }

    public List<String> getExcludedURL() {
        return excludedURL;
    }
    
    public String getUrl() {
        return url;
    }
    
    public List<MetaAndSha512> getFilesToAdd() {
        return filesToAdd;
    }

}
