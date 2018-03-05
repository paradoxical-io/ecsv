import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs/Observable";

@Injectable()
export class ApiService {

    constructor(private http: HttpClient) {
    }

    listServices(): Observable<Service[]> {
        return this.http.get<Service[]>("/api/v1/ecs/clusters/default/services")
    }
}

export class Service {
    name: String
    arn: String
    status: String
}