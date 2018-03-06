import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs/Observable";

@Injectable()
export class ApiService {

    constructor(private http: HttpClient) {
    }

    listServices(cluster: String, filter: string = ""): Observable<Service[]> {
        return this.http.get<Service[]>(`/api/v1/ecs/clusters/${cluster}/services?search=${filter}`);
    }

    listClusters(): Observable<Cluster[]> {
        return this.http.get<Cluster[]>("/api/v1/ecs/clusters");
    }

    getClusterDetail(arn: String): Observable<ClusterInstance> {
        return this.http.get<ClusterInstance>(`/api/v1/ecs/clusters/${arn}`);
    }
}

export class Service {
    name: String;
    arn: String;
    status: String;
    pending: number;
    desired: number
}

export class Cluster {
    name: String;
    arn: String;
    serviceCount: ServiceCount;
}

export class ClusterInstance {
    ec2HostId: String;
    serviceCount: ServiceCount;
}

export class ServiceCount {
    active: number;
    pending: number;
}